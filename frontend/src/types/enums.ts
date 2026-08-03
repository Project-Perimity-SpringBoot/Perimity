/** Every enum in the Perimity backend, transcribed from Java source. */

export const ROLES = [
  'SUPER_ADMIN', 'CAMPUS_ADMIN', 'FACULTY', 'STUDENT', 'VISITOR', 'GUARD',
] as const;
export type Role = (typeof ROLES)[number];

export const PASS_STATUSES = [
  'PENDING', 'ACTIVE', 'PAUSED', 'EXPIRED', 'REVOKED',
] as const;
export type PassStatus = (typeof PASS_STATUSES)[number];

export const PASS_TYPES = ['DAILY', 'EVENT'] as const;
export type PassType = (typeof PASS_TYPES)[number];

export const REQUEST_STATUSES = ['PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'] as const;
export type RequestStatus = (typeof REQUEST_STATUSES)[number];

export const BATCH_STATUSES = [
  'VALIDATING', 'VALIDATED', 'PROCESSING', 'COMPLETED', 'FAILED',
] as const;
export type BatchStatus = (typeof BATCH_STATUSES)[number];

export const SCAN_RESULTS = ['ALLOWED', 'AMBER', 'DENIED'] as const;
export type ScanResult = (typeof SCAN_RESULTS)[number];

export const DENIAL_REASONS = [
  'PASS_EXPIRED', 'PASS_REVOKED', 'PASS_PAUSED', 'PASS_PENDING',
  'INVALID_TOKEN', 'WRONG_CAMPUS', 'WRONG_GATE', 'OUT_OF_DATE_RANGE',
] as const;
export type DenialReason = (typeof DENIAL_REASONS)[number];

export type SessionState = 'OPEN' | 'CLOSED';
export type DocumentType = 'PHOTO' | 'ID_PROOF' | 'CERTIFICATE' | 'OTHER';
export type ProfileType = 'STUDENT' | 'FACULTY';
export type JobStatus = 'QUEUED' | 'PROCESSING' | 'DONE' | 'FAILED';
export type EmailStatus = 'PENDING' | 'SENT' | 'FAILED' | 'NO_RECIPIENT';
export type ConfigValueType = 'STRING' | 'BOOLEAN' | 'INTEGER' | 'JSON';

export type OtpPurpose =
  | 'LOGIN' | 'REGISTRATION' | 'VISITOR_VERIFICATION'
  | 'PASS_RETRIEVAL' | 'PASSWORD_RESET';

export type AuditAction =
  | 'LOGIN_SUCCESS' | 'LOGIN_FAILED' | 'LOGOUT' | 'ACCOUNT_LOCKED'
  | 'OTP_REQUESTED' | 'OTP_FAILED' | 'PASSWORD_CHANGED' | 'PASSWORD_RESET_REQUESTED'
  | 'ACCOUNT_CREATED' | 'ACCOUNT_DEACTIVATED'
  | 'REQUEST_APPROVED' | 'REQUEST_REJECTED' | 'PASS_REVOKED'
  | 'BLOCKLIST_ADDED' | 'BLOCKLIST_REMOVED' | 'BLOCKED_REGISTRATION_ATTEMPT'
  | 'CAMPUS_CONFIG_CHANGED' | 'SHIFT_STARTED' | 'SHIFT_ENDED'
  | 'BULK_BLOCKLIST_SCREENED' | 'BULK_IDENTITY_RESOLVED';

/* ── Derived rules, transcribed from Java. Import these; never reimplement. ── */

/** PassStatus.isScannable() */
export const isScannable = (s: PassStatus): boolean => s === 'ACTIVE';

/** PassStatus.allowedNextStates() — verbatim from the Java switch. */
export const PASS_TRANSITIONS: Readonly<Record<PassStatus, readonly PassStatus[]>> = {
  PENDING: ['ACTIVE', 'REVOKED'],
  ACTIVE: ['PAUSED', 'EXPIRED', 'REVOKED'],
  PAUSED: ['ACTIVE', 'REVOKED'],
  EXPIRED: [],
  REVOKED: [],
};

export const canTransitionTo = (from: PassStatus, to: PassStatus): boolean =>
  PASS_TRANSITIONS[from].includes(to);

/** GatePassStatusUpdateDto.isTargetStatusRequestable() */
export const REQUESTABLE_STATUSES = ['ACTIVE', 'PAUSED', 'REVOKED'] as const;
export type RequestableStatus = (typeof REQUESTABLE_STATUSES)[number];

export const canLoginWithPassword = (r: Role): boolean => r !== 'VISITOR';

/**
 * Role.canLoginWithOtp(). A password-only role that asks for a code gets a
 * normal 200 and no email ever arrives, so the UI must never offer it.
 */
export const canLoginWithOtp = (r: Role): boolean =>
  r === 'FACULTY' || r === 'STUDENT' || r === 'VISITOR';

/** SUPER_ADMIN has campusId null — this is what drives the B5 gating. */
export const requiresCampus = (r: Role): boolean => r !== 'SUPER_ADMIN';

/** Role.isStaff() as used by CurrentUser.requireSelfOrStaff. GUARD is NOT staff. */
export const isStaff = (r: Role): boolean =>
  r === 'SUPER_ADMIN' || r === 'CAMPUS_ADMIN' || r === 'FACULTY';

export const isAdministrative = (r: Role): boolean =>
  r === 'SUPER_ADMIN' || r === 'CAMPUS_ADMIN';

/** ScanResult.permitsEntry() — AMBER still lets the person in. */
export const permitsEntry = (r: ScanResult): boolean => r !== 'DENIED';

export const isTerminalJob = (s: JobStatus): boolean => s === 'DONE' || s === 'FAILED';

export const isSettledEmail = (s: EmailStatus): boolean =>
  s === 'SENT' || s === 'NO_RECIPIENT';
