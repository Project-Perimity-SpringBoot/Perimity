package com.perimity.user.bulk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.perimity.user.client.AuthFeignClient;
import com.perimity.user.entity.Department;
import com.perimity.user.entity.StudentImportRow;
import com.perimity.user.entity.StudentProfile;
import com.perimity.user.entity.enums.Gender;
import com.perimity.user.entity.enums.ImportRowOutcome;
import com.perimity.user.repository.DepartmentRepository;
import com.perimity.user.repository.StudentProfileRepository;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * The rules a bulk import must not quietly relax.
 *
 * ==========================================================================
 * WHY THIS CLASS MATTERS MORE THAN MOST
 * ==========================================================================
 * Every constraint here also exists on StudentSelfDetailsDto, where a student
 * filling in their own details is stopped by bean validation and told what is
 * wrong. This validator is the same rules applied to a spreadsheet - and a
 * spreadsheet arrives two hundred rows at a time, unattended, from a form
 * anybody with a link can submit to.
 *
 * If a rule stops firing here nothing errors. Two hundred rows import and the
 * damage shows up later: a name carrying a newline in an entry log, a roll
 * number colliding across a campus, a date of birth nobody can explain.
 *
 * The newline case is the one to guard hardest. PERSON_NAME uses a literal
 * space rather than \s precisely because a name with a line break in it forges
 * an audit entry - one log field becomes two lines and the second is
 * attacker-chosen.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImportRowValidatorTest {

    private static final Long BATCH = 1L;
    private static final Long CAMPUS = 2L;
    private static final String DEFAULT_CODE = "+91";

    @Mock private DepartmentRepository departmentRepository;
    @Mock private StudentProfileRepository studentRepository;
    @Mock private AuthFeignClient authClient;

    private ImportRowValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ImportRowValidator(departmentRepository, studentRepository, authClient);

        Department department = new Department();
        department.setId(9L);
        department.setCampusId(CAMPUS);
        department.setCode("IT");
        department.setName("Info tech");
        department.setActive(true);

        when(departmentRepository.findByCampusIdOrderByNameAsc(anyLong()))
                .thenReturn(List.of(department));
        when(studentRepository.findByCampusIdAndRollNoIgnoreCase(anyLong(), anyString()))
                .thenReturn(Optional.empty());

        /*
         * Nobody in the sheet has an account - the plain first-import case.
         *
         * An envelope with no data rather than a thrown FeignException.NotFound,
         * which is what the real endpoint produces. Both land on the same line
         * of the validator: the email is left unmapped. Constructing a real
         * FeignException needs a Request and a RequestTemplate, and a test that
         * spends five lines building one is testing Feign rather than this
         * class.
         */
        doReturn(new AuthFeignClient.UserEnvelope(false, "no account", null))
                .when(authClient).findByEmail(anyString());
    }

    /**
     * The lookup answering that this email already belongs to an account.
     *
     * doReturn rather than when(...).thenReturn: setUp has already stubbed
     * findByEmail for any argument, and the when() form would call the mock
     * while stubbing it.
     */
    private void emailBelongsTo(String email, Long userId) {
        doReturn(new AuthFeignClient.UserEnvelope(true, "ok",
                // name is null here on purpose: validation resolves ids only.
                // The name matters to StudentPassIssuer, which is not this test.
                new AuthFeignClient.UserView(userId, email, null, true)))
                .when(authClient).findByEmail(email);
    }

    /** An existing profile on this campus already holding the roll number. */
    private void rollNumberHeldBy(Long userId) {
        StudentProfile holder = new StudentProfile();
        holder.setUserId(userId);
        when(studentRepository.findByCampusIdAndRollNoIgnoreCase(anyLong(), anyString()))
                .thenReturn(Optional.of(holder));
    }

    /* ------------------------------------------------------------ helpers */

    /** A row that passes everything, so each test changes exactly one thing. */
    private Map<FormColumn, String> validValues() {
        Map<FormColumn, String> values = new EnumMap<>(FormColumn.class);
        values.put(FormColumn.EMAIL, "anjali@example.com");
        values.put(FormColumn.FULL_NAME, "Anjali Rao");
        values.put(FormColumn.FIRST_NAME, "Anjali");
        values.put(FormColumn.LAST_NAME, "Rao");
        values.put(FormColumn.DATE_OF_BIRTH, "2004-08-19");
        values.put(FormColumn.GENDER, "Female");
        values.put(FormColumn.ADDRESS, "12 Example Road");
        values.put(FormColumn.PHONE_NUMBER, "9876543210");
        values.put(FormColumn.ROLL_NO, "IT-001");
        values.put(FormColumn.DEPARTMENT, "Info tech");
        values.put(FormColumn.PHOTO, "1AbCdEfGhIjKlMnOpQrStUv");
        return values;
    }

    private StudentImportRow validateOne(Map<FormColumn, String> values) {
        return validator.validate(BATCH, CAMPUS,
                List.of(new ResponseSheetParser.ParsedRow(2, values)), DEFAULT_CODE).get(0);
    }

    private StudentImportRow withField(FormColumn column, String value) {
        Map<FormColumn, String> values = validValues();
        values.put(column, value);
        return validateOne(values);
    }

    /* -------------------------------------------------------- happy path */

    @Test
    @DisplayName("a complete row is accepted and every field is mapped")
    void acceptsAValidRow() {
        StudentImportRow row = validateOne(validValues());

        assertThat(row.getOutcome()).isEqualTo(ImportRowOutcome.PENDING);
        assertThat(row.getMessage()).isNull();
        assertThat(row.getEmail()).isEqualTo("anjali@example.com");
        assertThat(row.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(row.getDateOfBirth().toString()).isEqualTo("2004-08-19");
        assertThat(row.getDepartmentId()).isEqualTo(9L);
        assertThat(row.getRowNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("the country code defaults when the form has no column for it")
    void appliesTheDefaultCountryCode() {
        // Most forms will not ask. The default is configured per deployment, so
        // a campus outside India is a property change and not a code change.
        assertThat(validateOne(validValues()).getPhoneCountryCode()).isEqualTo("+91");
    }

    /* ------------------------------------------------- the security ones */

    @Test
    @DisplayName("a newline in a name is rejected - it forges an entry log line")
    void rejectsANewlineInAName() {
        /*
         * The single most important assertion in this class.
         *
         * Names are written to logs and rendered in a register. A name
         * containing \n turns one log field into two lines, and the second one
         * can be made to look like a real entry: "Ravi\n... Entry GRANTED".
         *
         * This passes only because PERSON_NAME uses a literal space rather than
         * \s - \s inside a character class also matches \n, \r and \t.
         */
        StudentImportRow row = withField(FormColumn.FIRST_NAME,
                "Ravi\n2026-08-05 09:14:22 INFO Entry GRANTED gate=MAIN");

        assertThat(row.getOutcome()).isEqualTo(ImportRowOutcome.REJECTED);
        assertThat(row.getMessage()).contains("first name");
    }

    @Test
    @DisplayName("a tab in a name is rejected too")
    void rejectsATabInAName() {
        assertThat(withField(FormColumn.LAST_NAME, "Rao\tAdmin").getOutcome())
                .isEqualTo(ImportRowOutcome.REJECTED);
    }

    @Test
    @DisplayName("digits in a name are rejected")
    void rejectsDigitsInAName() {
        // Students type things like "Ravi Kumar 2nd year" into a name box more
        // often than anyone would like.
        assertThat(withField(FormColumn.FIRST_NAME, "Ravi2").getOutcome())
                .isEqualTo(ImportRowOutcome.REJECTED);
    }

    /* ---------------------------------------------------------- the rest */

    @Test
    @DisplayName("an unknown gender label is rejected, never defaulted")
    void rejectsAnUnknownGenderLabel() {
        /*
         * The tempting shortcut is defaulting anything unrecognised to
         * PREFER_NOT_TO_SAY because it feels neutral. It would mean somebody
         * editing a form option silently rewrites what a whole intake said
         * about themselves, with nothing to show it happened.
         */
        StudentImportRow row = withField(FormColumn.GENDER, "Rather not answer");

        assertThat(row.getOutcome()).isEqualTo(ImportRowOutcome.REJECTED);
        assertThat(row.getGender()).isNull();
        // The message must list what IS accepted, so a form can be fixed
        // without reading any documentation.
        assertThat(row.getMessage()).contains("Male", "Female", "Prefer not to say");
    }

    @Test
    @DisplayName("gender labels are matched case-insensitively")
    void acceptsGenderLabelVariants() {
        assertThat(withField(FormColumn.GENDER, "male").getGender()).isEqualTo(Gender.MALE);
        assertThat(withField(FormColumn.GENDER, "PREFER NOT TO SAY").getGender())
                .isEqualTo(Gender.PREFER_NOT_TO_SAY);
    }

    @Test
    @DisplayName("a five digit +91 mobile is rejected")
    void rejectsAShortIndianMobile() {
        StudentImportRow row = withField(FormColumn.PHONE_NUMBER, "12345");

        assertThat(row.getOutcome()).isEqualTo(ImportRowOutcome.REJECTED);
        assertThat(row.getMessage()).contains("12345");
    }

    @Test
    @DisplayName("the ten digit rule applies only to +91")
    void doesNotApplyTheIndianRuleToOtherCountries() {
        /*
         * A campus-agnostic product cannot bake one country's phone length into
         * a validator. A UK number is not an invalid Indian one.
         */
        Map<FormColumn, String> values = validValues();
        values.put(FormColumn.PHONE_COUNTRY_CODE, "+44");
        values.put(FormColumn.PHONE_NUMBER, "7700900123");

        assertThat(validateOne(values).getOutcome()).isEqualTo(ImportRowOutcome.PENDING);
    }

    @Test
    @DisplayName("an ambiguous date is rejected rather than guessed")
    void rejectsAnAmbiguousDate() {
        // 05/08/2004 is 5 August or 8 May depending on where the reader lives.
        // Guessing would put a wrong date of birth on a record nobody would
        // ever think to check.
        StudentImportRow row = withField(FormColumn.DATE_OF_BIRTH, "05/08/2004");

        assertThat(row.getOutcome()).isEqualTo(ImportRowOutcome.REJECTED);
        assertThat(row.getDateOfBirth()).isNull();
    }

    @Test
    @DisplayName("an implausible year is rejected")
    void rejectsAnImplausibleDateOfBirth() {
        assertThat(withField(FormColumn.DATE_OF_BIRTH, "1823-04-01").getOutcome())
                .isEqualTo(ImportRowOutcome.REJECTED);
        assertThat(withField(FormColumn.DATE_OF_BIRTH, "2025-01-01").getOutcome())
                .isEqualTo(ImportRowOutcome.REJECTED);
    }

    @Test
    @DisplayName("a department that is not on this campus is rejected")
    void rejectsAnUnknownDepartment() {
        StudentImportRow row = withField(FormColumn.DEPARTMENT, "Astrophysics");

        assertThat(row.getOutcome()).isEqualTo(ImportRowOutcome.REJECTED);
        assertThat(row.getMessage()).contains("Astrophysics");
    }

    @Test
    @DisplayName("a department resolves by code as well as by name")
    void resolvesDepartmentByCode() {
        // A form dropdown might carry either, and both are unambiguous.
        assertThat(withField(FormColumn.DEPARTMENT, "IT").getDepartmentId()).isEqualTo(9L);
        assertThat(withField(FormColumn.DEPARTMENT, "IT - Info tech").getDepartmentId())
                .isEqualTo(9L);
    }

    @Test
    @DisplayName("a missing photo link is rejected")
    void rejectsAMissingPhoto() {
        // A student with no photo cannot hold a pass - a guard would have no
        // face to check against the person at the gate.
        assertThat(withField(FormColumn.PHOTO, null).getOutcome())
                .isEqualTo(ImportRowOutcome.REJECTED);
    }

    /* --------------------------------------------- rules about the SHEET */

    @Test
    @DisplayName("the same email twice in one sheet rejects the second row")
    void rejectsADuplicateEmailWithinTheSheet() {
        /*
         * users.email is UNIQUE. Two rows with the same new address would make
         * the insert throw and roll back every account in the batch, so one
         * repeated address must not cost two hundred students their accounts.
         */
        List<StudentImportRow> rows = validator.validate(BATCH, CAMPUS, List.of(
                new ResponseSheetParser.ParsedRow(2, validValues()),
                new ResponseSheetParser.ParsedRow(3, validValues())), DEFAULT_CODE);

        assertThat(rows.get(0).getOutcome()).isEqualTo(ImportRowOutcome.PENDING);
        assertThat(rows.get(1).getOutcome()).isEqualTo(ImportRowOutcome.REJECTED);
        assertThat(rows.get(1).getMessage()).contains("more than once");
    }

    @Test
    @DisplayName("the same roll number twice in one sheet rejects the second row")
    void rejectsADuplicateRollNumberWithinTheSheet() {
        Map<FormColumn, String> second = validValues();
        second.put(FormColumn.EMAIL, "someone.else@example.com");

        List<StudentImportRow> rows = validator.validate(BATCH, CAMPUS, List.of(
                new ResponseSheetParser.ParsedRow(2, validValues()),
                new ResponseSheetParser.ParsedRow(3, second)), DEFAULT_CODE);

        assertThat(rows.get(1).getOutcome()).isEqualTo(ImportRowOutcome.REJECTED);
        assertThat(rows.get(1).getMessage()).contains("IT-001");
    }

    @Test
    @DisplayName("a roll number held by a DIFFERENT student is rejected")
    void rejectsARollNumberTakenByAnotherStudentOnThisCampus() {
        rollNumberHeldBy(500L);
        emailBelongsTo("anjali@example.com", 501L);

        StudentImportRow row = validateOne(validValues());

        assertThat(row.getOutcome()).isEqualTo(ImportRowOutcome.REJECTED);
        assertThat(row.getMessage()).contains("by a different student");
    }

    @Test
    @DisplayName("a student resubmitting the form keeps their own roll number")
    void acceptsAStudentReusingTheirOwnRollNumber() {
        /*
         * The case this whole lookup exists for, and the one that used to be
         * rejected. Students resubmit forms constantly - a first import,
         * then a correction, then a photo they actually like.
         *
         * The roll number is taken and the taker is this very row.
         */
        rollNumberHeldBy(500L);
        emailBelongsTo("anjali@example.com", 500L);

        StudentImportRow row = validateOne(validValues());

        assertThat(row.getOutcome()).isEqualTo(ImportRowOutcome.PENDING);
        assertThat(row.getMessage()).isNull();
    }

    @Test
    @DisplayName("a taken roll number is still rejected when the lookup is unavailable")
    void rejectsATakenRollNumberWhenAuthServiceIsDown() {
        /*
         * Degrading to the old behaviour is deliberate. With no way to tell
         * whose roll number it is, reporting the row beats importing it onto
         * somebody else's record - and faculty gets rows to fix rather than an
         * upload that will not start.
         */
        rollNumberHeldBy(500L);
        doThrow(new IllegalStateException("down")).when(authClient).findByEmail(anyString());

        StudentImportRow row = validateOne(validValues());

        assertThat(row.getOutcome()).isEqualTo(ImportRowOutcome.REJECTED);
        assertThat(row.getMessage()).contains("already used");
    }

    @Test
    @DisplayName("one bad row never affects the rows around it")
    void doesNotLetOneBadRowAffectTheOthers() {
        /*
         * The rule the whole bulk engine runs on. A 600 row sheet with row 34
         * broken must still produce 599 good rows and a report naming row 34.
         */
        Map<FormColumn, String> broken = validValues();
        broken.put(FormColumn.EMAIL, "second@example.com");
        broken.put(FormColumn.GENDER, "nonsense");

        Map<FormColumn, String> third = validValues();
        third.put(FormColumn.EMAIL, "third@example.com");
        third.put(FormColumn.ROLL_NO, "IT-003");

        List<StudentImportRow> rows = validator.validate(BATCH, CAMPUS, List.of(
                new ResponseSheetParser.ParsedRow(2, validValues()),
                new ResponseSheetParser.ParsedRow(3, broken),
                new ResponseSheetParser.ParsedRow(4, third)), DEFAULT_CODE);

        assertThat(rows.get(0).getOutcome()).isEqualTo(ImportRowOutcome.PENDING);
        assertThat(rows.get(1).getOutcome()).isEqualTo(ImportRowOutcome.REJECTED);
        assertThat(rows.get(2).getOutcome()).isEqualTo(ImportRowOutcome.PENDING);
    }

    @Test
    @DisplayName("every problem on a row is reported, not just the first")
    void reportsEveryProblemOnARow() {
        // Fixing a sheet one error per upload is a miserable way to spend an
        // afternoon.
        Map<FormColumn, String> values = validValues();
        values.put(FormColumn.GENDER, "nonsense");
        values.put(FormColumn.PHONE_NUMBER, "12345");

        String message = validateOne(values).getMessage();

        assertThat(message).contains("gender");
        assertThat(message).contains("12345");
    }
}
