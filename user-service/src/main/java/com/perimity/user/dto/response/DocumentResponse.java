package com.perimity.user.dto.response;

import com.perimity.user.entity.Document;
import com.perimity.user.entity.enums.DocumentType;
import java.time.LocalDateTime;

/**
 * Read model for one stored document.
 *
 * s3Key is returned, not a public URL. Handing out a permanent link to someone's
 * id proof would make it readable by anyone who ever sees the JSON. The client
 * asks for a short-lived pre-signed URL separately, when it actually needs to
 * display the file.
 */
public record DocumentResponse(
        Long id,
        Long userId,
        DocumentType docType,
        String s3Key,
        String fileName,
        String mimeType,
        boolean verified,
        Long verifiedBy,
        LocalDateTime verifiedAt,
        LocalDateTime createdAt
) {

    public static DocumentResponse from(Document e) {
        return new DocumentResponse(
                e.getId(),
                e.getUserId(),
                e.getDocType(),
                e.getS3Key(),
                e.getFileName(),
                e.getMimeType(),
                e.isVerified(),
                e.getVerifiedBy(),
                e.getVerifiedAt(),
                e.getCreatedAt()
        );
    }
}
