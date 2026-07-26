package com.perimity.user.entity.enums;

/**
 * The classes of file a profile can carry. The actual bytes live on S3; the
 * database stores only the key. Kept campus-agnostic - no institution-specific
 * document names.
 */
public enum DocumentType {
    PHOTO,
    ID_PROOF,
    CERTIFICATE,
    OTHER
}
