package com.perimity.user.entity;

import com.perimity.user.entity.enums.Gender;
import com.perimity.user.entity.enums.ImportRowOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row of an uploaded sheet, as parsed.
 *
 * ==========================================================================
 * WHY THE PARSED VALUES ARE STORED AND NOT RE-READ
 * ==========================================================================
 * Validation and confirmation are separate requests, so the sheet would have to
 * be uploaded twice or held somewhere between them. Storing the parsed row
 * means faculty confirm EXACTLY what they were shown - not a second read of a
 * file that could have been swapped in between.
 *
 * ==========================================================================
 * THIS TABLE HOLDS PERSONAL DATA
 * ==========================================================================
 * Dates of birth, addresses and phone numbers for a whole intake. It exists to
 * bridge two requests, and once a batch is COMPLETED the values are duplicated
 * in the profiles where they belong.
 *
 * So rows should be cleared once a batch is finished and old - a scheduled
 * sweep, the way qr-service prunes delivered jobs. Not built yet. Written down
 * here because an intake table quietly accumulating every student's address for
 * years is the kind of thing nobody notices until it matters.
 *
 * No photo bytes, ever. Only the Drive file id; the image goes to storage.
 */
@Entity
@Table(
        name = "student_import_rows",
        indexes = {
                @Index(name = "idx_import_row_batch", columnList = "batch_id"),
                @Index(name = "idx_import_row_email", columnList = "email")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentImportRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    /**
     * The row's position in the sheet, 1-based and counting the header.
     *
     * So an error can say "row 47" and faculty can open the spreadsheet and
     * look at row 47. An index into a filtered list would be a number that
     * matches nothing they can see.
     */
    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    /* ---- as parsed. Nulls are allowed: a rejected row still gets stored,
           because the preview has to show what was wrong with it. ---- */

    @Size(max = 180)
    @Column(name = "email", length = 180)
    private String email;

    @Size(max = 120)
    @Column(name = "full_name", length = 120)
    private String fullName;

    @Size(max = 60)
    @Column(name = "first_name", length = 60)
    private String firstName;

    @Size(max = 60)
    @Column(name = "middle_name", length = 60)
    private String middleName;

    @Size(max = 60)
    @Column(name = "last_name", length = 60)
    private String lastName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 20)
    private Gender gender;

    @Size(max = 250)
    @Column(name = "address", length = 250)
    private String address;

    @Size(max = 5)
    @Column(name = "phone_country_code", length = 5)
    private String phoneCountryCode;

    @Size(max = 15)
    @Column(name = "phone_number", length = 15)
    private String phoneNumber;

    @Size(max = 32)
    @Column(name = "roll_no", length = 32)
    private String rollNo;

    /** As typed in the form dropdown. Resolved to a department id at confirm. */
    @Size(max = 150)
    @Column(name = "department_label", length = 150)
    private String departmentLabel;

    @Column(name = "department_id")
    private Long departmentId;

    /**
     * The Drive file id pulled out of the URL the form put in the sheet, not
     * the URL itself. The id is the stable part; the surrounding link format
     * has changed more than once.
     */
    @Size(max = 128)
    @Column(name = "photo_drive_id", length = 128)
    private String photoDriveId;

    /* ---- outcome ---- */

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 20)
    @Builder.Default
    private ImportRowOutcome outcome = ImportRowOutcome.PENDING;

    /**
     * Why this row was rejected, or what happened to it. Written for faculty,
     * not for a log: "roll number CS-101 is already used on this campus", not
     * "constraint uk_student_campus_roll violated".
     */
    @Size(max = 500)
    @Column(name = "message", length = 500)
    private String message;

    /** The account this row created or matched. Null until written. */
    @Column(name = "user_id")
    private Long userId;
}
