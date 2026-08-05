package com.perimity.user.bulk;

import com.perimity.user.entity.Department;
import com.perimity.user.entity.StudentImportRow;
import com.perimity.user.entity.enums.Gender;
import com.perimity.user.entity.enums.ImportRowOutcome;
import com.perimity.user.repository.DepartmentRepository;
import com.perimity.user.repository.StudentProfileRepository;
import com.perimity.user.validation.ValidationPatterns;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Checks one parsed row and turns it into a StudentImportRow.
 *
 * ==========================================================================
 * THE SAME RULES AS THE FORM A STUDENT FILLS IN THEMSELVES
 * ==========================================================================
 * Every constraint on StudentSelfDetailsDto applies here too, and for the same
 * reasons. An import that accepted what the in-app form refuses would be a way
 * around the rules rather than a faster path to the same place - and the rule
 * these rows would most obviously escape is the newline check on names, which
 * exists because a name with a line break in it forges an entry log.
 *
 * ==========================================================================
 * EVERY MESSAGE IS WRITTEN FOR FACULTY
 * ==========================================================================
 * "Row 47: roll number CS-101 is already used on this campus", not a constraint
 * name. The person reading this has a spreadsheet open and needs to know which
 * cell to change.
 *
 * ==========================================================================
 * ONE BAD ROW NEVER FAILS THE BATCH
 * ==========================================================================
 * Same rule as the visitor bulk engine and the expiry sweep. A row that cannot
 * be used is REJECTED with a reason and the rest carry on.
 */
@Component
public class ImportRowValidator {

    /** Nobody at a campus was born before this, or within the last ten years. */
    private static final LocalDate DOB_FLOOR = LocalDate.of(1900, 1, 1);
    private static final int MIN_AGE_YEARS = 10;

    private final DepartmentRepository departmentRepository;
    private final StudentProfileRepository studentRepository;

    public ImportRowValidator(DepartmentRepository departmentRepository,
                              StudentProfileRepository studentRepository) {
        this.departmentRepository = departmentRepository;
        this.studentRepository = studentRepository;
    }

    /**
     * Validates every row of a batch.
     *
     * Takes the whole list rather than one row at a time because two of the
     * checks are about the SHEET, not the row: a roll number used twice within
     * one upload, and an email that appears twice. Neither is visible from a
     * single row, and both would otherwise fail at the database with a message
     * nobody can map back to a line in a spreadsheet.
     */
    public List<StudentImportRow> validate(Long batchId,
                                           Long campusId,
                                           List<ResponseSheetParser.ParsedRow> parsed,
                                           String defaultCountryCode) {

        Map<String, Department> departments = departmentsByLabel(campusId);

        // Both are lower-cased, because "CS-101" and "cs-101" are the same roll
        // number to the unique index and the same person to a human.
        Set<String> emailsSeen = new HashSet<>();
        Set<String> rollNosSeen = new HashSet<>();

        List<StudentImportRow> rows = new ArrayList<>(parsed.size());

        for (ResponseSheetParser.ParsedRow source : parsed) {
            rows.add(validateOne(batchId, campusId, source, departments,
                    emailsSeen, rollNosSeen, defaultCountryCode));
        }
        return rows;
    }

    private StudentImportRow validateOne(Long batchId,
                                         Long campusId,
                                         ResponseSheetParser.ParsedRow source,
                                         Map<String, Department> departments,
                                         Set<String> emailsSeen,
                                         Set<String> rollNosSeen,
                                         String defaultCountryCode) {

        StudentImportRow row = StudentImportRow.builder()
                .batchId(batchId)
                .rowNumber(source.rowNumber())
                .outcome(ImportRowOutcome.PENDING)
                .build();

        List<String> problems = new ArrayList<>();

        /* ---------------------------------------------------------- email */
        String email = lower(source.get(FormColumn.EMAIL));
        row.setEmail(email);
        if (email == null) {
            problems.add("no email address");
        } else if (!email.matches(ValidationPatterns.EMAIL)) {
            problems.add("\"" + email + "\" is not a valid email address");
        } else if (!emailsSeen.add(email)) {
            // Students resubmit forms. Keeping the LAST response is usually
            // what is meant, but silently discarding an earlier row would hide
            // the fact that two exist, so it is reported instead.
            problems.add("this email appears more than once in the sheet");
        }

        /* ----------------------------------------------------------- names */
        row.setFullName(source.get(FormColumn.FULL_NAME));
        row.setFirstName(source.get(FormColumn.FIRST_NAME));
        row.setMiddleName(source.get(FormColumn.MIDDLE_NAME));
        row.setLastName(source.get(FormColumn.LAST_NAME));

        requireName(problems, "full name", row.getFullName(), ValidationPatterns.PERSON_NAME);
        requireName(problems, "first name", row.getFirstName(), ValidationPatterns.PERSON_NAME_PART);
        requireName(problems, "last name", row.getLastName(), ValidationPatterns.PERSON_NAME_PART);

        if (row.getMiddleName() != null
                && !row.getMiddleName().matches(ValidationPatterns.PERSON_NAME_PART)) {
            problems.add("middle name has characters that are not allowed");
        }

        /* --------------------------------------------------- date of birth */
        String dobText = source.get(FormColumn.DATE_OF_BIRTH);
        if (dobText == null) {
            problems.add("no date of birth");
        } else {
            Optional<LocalDate> dob = parseDate(dobText);
            if (dob.isEmpty()) {
                problems.add("\"" + dobText + "\" is not a date the importer understands "
                        + "(use YYYY-MM-DD in the form)");
            } else if (!isPlausible(dob.get())) {
                problems.add("date of birth " + dob.get() + " looks wrong");
            } else {
                row.setDateOfBirth(dob.get());
            }
        }

        /* --------------------------------------------------------- gender */
        String genderLabel = source.get(FormColumn.GENDER);
        if (genderLabel == null) {
            problems.add("no gender");
        } else {
            Optional<Gender> gender = GenderLabels.parse(genderLabel);
            if (gender.isEmpty()) {
                problems.add("gender \"" + genderLabel + "\" is not one of: "
                        + GenderLabels.acceptedLabels());
            } else {
                row.setGender(gender.get());
            }
        }

        /* -------------------------------------------------------- address */
        String address = source.get(FormColumn.ADDRESS);
        row.setAddress(address);
        if (address == null) {
            problems.add("no address");
        } else if (address.length() > 250) {
            problems.add("address is longer than 250 characters");
        }

        /* ---------------------------------------------------------- phone */
        String code = source.get(FormColumn.PHONE_COUNTRY_CODE);
        // Missing entirely is normal - most forms will not ask. The default is
        // configured per deployment, never a literal in this file.
        code = code == null ? defaultCountryCode : code;
        if (!code.startsWith("+")) {
            code = "+" + code;
        }
        row.setPhoneCountryCode(code);

        String phone = digitsOnly(source.get(FormColumn.PHONE_NUMBER));
        row.setPhoneNumber(phone);

        if (phone == null) {
            problems.add("no phone number");
        } else if (!code.matches("^\\+[1-9][0-9]{0,3}$")) {
            problems.add("country code \"" + code + "\" is not valid");
        } else if (!phone.matches("^[0-9]{4,15}$")) {
            problems.add("phone number \"" + phone + "\" is not digits only");
        } else if ("+91".equals(code) && !phone.matches("^[6-9][0-9]{9}$")) {
            // Country-specific, and conditional on the country - the same rule
            // and the same reasoning as StudentSelfDetailsDto.
            problems.add("\"" + phone + "\" is not a valid Indian mobile number");
        }

        /* ----------------------------------------------------- roll number */
        String rollNo = source.get(FormColumn.ROLL_NO);
        row.setRollNo(rollNo);
        if (rollNo == null) {
            problems.add("no roll number");
        } else if (!rollNo.matches(ValidationPatterns.IDENTIFIER_CODE)) {
            problems.add("roll number \"" + rollNo + "\" may contain letters, digits, "
                    + "hyphens and slashes only");
        } else if (!rollNosSeen.add(rollNo.toLowerCase(Locale.ROOT))) {
            problems.add("roll number \"" + rollNo + "\" appears more than once in the sheet");
        } else if (studentRepository.findByCampusIdAndRollNoIgnoreCase(campusId, rollNo)
                .isPresent()) {
            /*
             * Unique per campus, never globally - two campuses may run the same
             * numbering scheme and neither is wrong.
             *
             * This CANNOT tell whether the existing holder is the same student
             * resubmitting the form, which is the common case. user-service
             * knows profiles by account id and the sheet only has an email, and
             * resolving one to the other means asking auth-service - a call per
             * row, at validation time, for a question that confirm answers for
             * free once the account is known.
             *
             * So the message says both possibilities rather than asserting the
             * wrong one. Confirm does the real check: if the roll number
             * belongs to the account behind this email, it is an UPDATE, not a
             * collision.
             */
            problems.add("roll number \"" + rollNo + "\" is already used on this campus "
                    + "(fine if this is the same student resubmitting)");
        }

        /* ----------------------------------------------------- department */
        String label = source.get(FormColumn.DEPARTMENT);
        row.setDepartmentLabel(label);
        if (label == null) {
            problems.add("no department");
        } else {
            Department department = departments.get(normaliseLabel(label));
            if (department == null) {
                problems.add("department \"" + label + "\" does not exist on this campus");
            } else if (!department.isActive()) {
                problems.add("department \"" + department.getName() + "\" has been retired");
            } else {
                row.setDepartmentId(department.getId());
            }
        }

        /* ---------------------------------------------------------- photo */
        String driveId = source.get(FormColumn.PHOTO);
        row.setPhotoDriveId(driveId);
        if (driveId == null) {
            // A hard failure, not a warning. A student with no photo cannot
            // hold a pass, because a guard would have no face to check against
            // the person at the gate. Importing them would produce an account
            // that looks complete and cannot get through the door.
            problems.add("no passport photo, or the cell is not a Google Drive link");
        }

        if (!problems.isEmpty()) {
            row.setOutcome(ImportRowOutcome.REJECTED);
            row.setMessage(truncate(String.join("; ", problems)));
        }
        return row;
    }

    /**
     * Departments keyed by every label a form might legitimately use: the name,
     * the code, and "CODE - Name" which is how a dropdown is often written.
     *
     * Built once per batch. A lookup per row would be a query per row, and an
     * intake is two hundred rows.
     */
    private Map<String, Department> departmentsByLabel(Long campusId) {
        Map<String, Department> byLabel = new HashMap<>();
        for (Department department : departmentRepository.findByCampusIdOrderByNameAsc(campusId)) {
            byLabel.put(normaliseLabel(department.getName()), department);
            byLabel.put(normaliseLabel(department.getCode()), department);
            byLabel.put(normaliseLabel(department.getCode() + " - " + department.getName()),
                    department);
            byLabel.put(normaliseLabel(department.getName() + " (" + department.getCode() + ")"),
                    department);
        }
        return byLabel;
    }

    private void requireName(List<String> problems, String label, String value, String pattern) {
        if (value == null) {
            problems.add("no " + label);
        } else if (!value.matches(pattern)) {
            problems.add(label + " has characters that are not allowed "
                    + "(letters, spaces, apostrophes, hyphens and full stops only)");
        }
    }

    /**
     * ISO first, because the parser converts real date cells to it. The other
     * shapes are for a form that used a text question, where whatever the
     * student typed is all there is.
     *
     * Ambiguous formats are deliberately absent. "05/08/2004" is 5 August or
     * 8 May depending on where the reader lives, and guessing would put a wrong
     * date of birth on a record nobody would ever think to check.
     */
    private Optional<LocalDate> parseDate(String text) {
        String cleaned = text.trim();
        try {
            return Optional.of(LocalDate.parse(cleaned));
        } catch (DateTimeParseException ignored) {
            // Not ISO. Fall through.
        }
        for (String pattern : new String[]{"yyyy/MM/dd", "yyyy.MM.dd"}) {
            try {
                return Optional.of(LocalDate.parse(cleaned,
                        java.time.format.DateTimeFormatter.ofPattern(pattern)));
            } catch (DateTimeParseException ignored) {
                // Try the next.
            }
        }
        return Optional.empty();
    }

    private boolean isPlausible(LocalDate dob) {
        return dob.isAfter(DOB_FLOOR) && dob.isBefore(LocalDate.now().minusYears(MIN_AGE_YEARS));
    }

    private static String normaliseLabel(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ").trim();
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT).trim();
    }

    /** Strips spaces, dashes and brackets people type into a phone field. */
    private static String digitsOnly(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? null : digits;
    }

    /** The message column is 500 characters; a row with many problems can exceed it. */
    private static String truncate(String message) {
        return message.length() <= 500 ? message : message.substring(0, 497) + "...";
    }
}
