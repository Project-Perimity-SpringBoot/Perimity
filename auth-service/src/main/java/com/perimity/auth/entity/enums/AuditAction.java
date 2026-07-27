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
    CAMPUS_CONFIG_CHANGED
}
