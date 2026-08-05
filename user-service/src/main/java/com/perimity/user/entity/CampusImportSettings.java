package com.perimity.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Where a campus keeps its intake form.
 *
 * ==========================================================================
 * WHY THIS IS STORED PER CAMPUS AND NOT IN A PROPERTY FILE
 * ==========================================================================
 * A form belongs to the faculty who made it, in their own Google account, with
 * their own questions. Two campuses will have two different forms, and a
 * campus that renames its departments will edit its own.
 *
 * Putting a form URL in application.properties would make one campus's form the
 * product's form. The same rule that keeps institution names out of the code
 * applies here: the deployment is campus-agnostic, and anything a campus owns
 * belongs in a row rather than in a config file.
 *
 * ==========================================================================
 * THE FORM IS NOT CREATED BY THIS SYSTEM
 * ==========================================================================
 * It cannot be. A Google Form has to be OWNED by a Google user, and this
 * service authenticates as a service account which cannot own something
 * students can reach.
 *
 * So faculty copy a template form into their own account, link a responses
 * sheet, and paste both URLs here. After that the app can share the form and
 * read the sheet without anyone touching Google again.
 */
@Entity
@Table(
        name = "campus_import_settings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_import_settings_campus", columnNames = "campus_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampusImportSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** One set of settings per campus, enforced by the unique index. */
    @NotNull
    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    /**
     * The public link students open. Whatever Google gave faculty when they
     * pressed Send - a /viewform URL or a forms.gle short link.
     *
     * Stored as given rather than normalised. A link that works when pasted is
     * worth more than a tidy one, and Google has changed these formats before.
     */
    @Size(max = 500)
    @Column(name = "form_url", length = 500)
    private String formUrl;

    /**
     * The id of the SHEET the form writes responses to - not the form.
     *
     * These are two different documents and mixing them up is the obvious
     * mistake: a form id here means every download returns the questions rather
     * than the answers. The service extracts the id from a pasted URL so nobody
     * has to know which part of it matters.
     *
     * The sheet must be shared with the service account, read-only. Without
     * that, Drive authenticates fine and then returns 404 for it - which reads
     * like a wrong id rather than a permissions gap.
     */
    @Size(max = 128)
    @Column(name = "responses_sheet_id", length = 128)
    private String responsesSheetId;

    /** Who last changed these, for the "set up by" line on the screen. */
    @Column(name = "updated_by")
    private Long updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Both halves are needed before the screen can offer Pull or Download. */
    public boolean isComplete() {
        return formUrl != null && !formUrl.isBlank()
                && responsesSheetId != null && !responsesSheetId.isBlank();
    }
}
