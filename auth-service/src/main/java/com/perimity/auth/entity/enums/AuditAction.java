package com.perimity.auth.entity.enums;

/**
 * Every action that must produce an audit row (FR-AUD-1).
 * The audit log is append-only - entries are never updated or deleted.
 */
public enum AuditAction {
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    LOGOUT,
    ACCOUNT_LOCKED,
    OTP_REQUESTED,
    OTP_FAILED,
    PASSWORD_CHANGED,
    PASSWORD_RESET_REQUESTED,
    ACCOUNT_CREATED,
    ACCOUNT_DEACTIVATED,
    REQUEST_APPROVED,
    REQUEST_REJECTED,
    PASS_REVOKED,
    BLOCKLIST_ADDED,
    BLOCKLIST_REMOVED,
    BLOCKED_REGISTRATION_ATTEMPT,
    CAMPUS_CONFIG_CHANGED,

    /**
     * Day 10. One row per bulk batch, not per spreadsheet row.
     *
     * FR-BLK-5 requires a blocked attempt to be recorded. Taken per-row, a
     * 600-row sheet where every row is blocked writes 600 audit rows in 600
     * separate transactions (AuditService is REQUIRES_NEW), and the Campus
     * Admin's audit view from FR-AUD-3 becomes unreadable for anyone looking
     * for a login failure that week.
     *
     * These two actions record the batch: how many rows, how many refused, and
     * which row numbers. The per-row detail belongs in the error report the
     * uploader downloads, which is where they will actually look for it.
     */
    BULK_BLOCKLIST_SCREENED,
    BULK_IDENTITY_RESOLVED
}
