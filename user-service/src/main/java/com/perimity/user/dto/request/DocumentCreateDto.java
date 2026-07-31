package com.perimity.user.dto.request;

import com.perimity.user.entity.enums.DocumentType;
import com.perimity.user.validation.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of POST /api/user/documents - registers a file that has already been
 * uploaded to object storage.
 *
 * verified, verifiedBy and verifiedAt are absent by design. If a client could
 * send verified = true it would verify its own id proof, which defeats the
 * entire point of having a verification step.
 *
 * The mime type here is what the client claims. The service layer must check
 * the stored object's real content type before trusting it - a file is renamed
 * in one second.
 */
@Schema(description = "Register an uploaded document against a person")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentCreateDto {

    @NotNull(message = "User account is required")
    @Positive(message = "User id must be a positive number")
    @Schema(example = "108")
    private Long userId;

    @NotNull(message = "Document type is required")
    @Schema(description = "PHOTO, ID_PROOF, CERTIFICATE or OTHER", example = "ID_PROOF")
    private DocumentType docType;

    @NotBlank(message = "Storage key is required")
    @Size(max = 512)
    @Pattern(regexp = ValidationPatterns.OBJECT_KEY, message = ValidationPatterns.OBJECT_KEY_MESSAGE)
    @Schema(example = "profiles/user-108/id-proof.pdf")
    private String s3Key;

    @NotBlank(message = "File name is required")
    @Size(max = 255)
    @Pattern(regexp = "^[^\\\\/:*?\"<>|\\r\\n]{1,255}$",
             message = "File name contains characters that are not allowed")
    @Schema(example = "id-proof.pdf")
    private String fileName;

    @Size(max = 100)
    @Pattern(regexp = "^$|^[a-z]+/[a-zA-Z0-9.+_-]+$", message = "Invalid mime type")
    @Schema(description = "Client-declared. The service layer must verify it against the stored object.",
            example = "application/pdf")
    private String mimeType;
}
