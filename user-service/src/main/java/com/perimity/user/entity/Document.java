package com.perimity.user.entity;

import com.perimity.user.entity.enums.DocumentType;
import com.perimity.user.validation.ValidationPatterns;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * A file attached to a person - a photo, an id proof, a certificate. The bytes
 * live on S3; this row stores the key, the original file name, the mime type
 * and whether an admin has verified it.
 *
 * Append-and-verify, not edit: a document is uploaded once and then either
 * verified or rejected, so there is no updated_at. The only fields that change
 * after insert are the three verification ones below, written by an admin.
 */
@Entity
@Table(
        name = "documents",
        indexes = {
                @Index(name = "idx_documents_user", columnList = "user_id"),
                @Index(name = "idx_documents_type", columnList = "user_id, doc_type")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The auth-service account this document belongs to. */
    @NotNull
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, length = 20)
    private DocumentType docType;

    /** S3 key only. The regex blocks a crafted key from escaping its prefix. */
    @NotBlank
    @Size(max = 512)
    @Pattern(regexp = ValidationPatterns.OBJECT_KEY, message = ValidationPatterns.OBJECT_KEY_MESSAGE)
    @Column(name = "s3_key", nullable = false, length = 512)
    private String s3Key;

    @NotBlank
    @Size(max = 255)
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Size(max = 100)
    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private boolean verified = false;

    @Column(name = "verified_by")
    private Long verifiedBy;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    /**
     * Why a document was rejected. Mandatory on rejection - DocumentVerificationDto
     * refuses a rejection with no remarks, and this is where that text lands.
     *
     * Added on Day 6. Without it the rejection reason was validated, accepted,
     * and then dropped on the floor: the person was told "rejected" with no way
     * to learn what to fix, and would upload the same file again.
     */
    @Size(max = 500)
    @Column(name = "verification_remarks", length = 500)
    private String verificationRemarks;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
