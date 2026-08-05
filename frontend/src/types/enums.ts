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

/**
 * Where a student's self-declared details are in the verification cycle.
 * Mirrors ProfileVerificationStatus in user-service — keep the two identical.
 *
 *   DRAFT     -> SUBMITTED   student finished and asked to be checked
 *   SUBMITTED -> VERIFIED    faculty accepted
 *   SUBMITTED -> REJECTED    faculty refused, with mandatory remarks
 *   REJECTED  -> SUBMITTED   student corrected it and asked again
 *   VERIFIED  -> DRAFT       student edited a verified profile
 *
 * An array, not a bare union, because the queue screen needs to iterate it.
 */
export const PROFILE_VERIFICATION_STATUSES = [
  'DRAFT', 'SUBMITTED', 'VERIFIED', 'REJECTED',
] as const;
export type ProfileVerificationStatus = (typeof PROFILE_VERIFICATION_STATUSES)[number];

/**
 * The server permits editing in DRAFT and REJECTED only. SUBMITTED is locked
 * while faculty read it; VERIFIED is editable but as an explicit act that
 * clears the verification, so the form asks first rather than silently
 * discarding somebody's approval.
 *
 * This mirrors ProfileVerificationStatus.isStudentEditable() and is a UI hint
 * ONLY — the server enforces it regardless of what this says.
 */
export const isProfileEditable = (status: ProfileVerificationStatus | null | undefined): boolean =>
  status === 'DRAFT' || status === 'REJECTED' || status === 'VERIFIED';

export const canSubmitProfile = (status: ProfileVerificationStatus | null | undefined): boolean =>
  status === 'DRAFT' || status === 'REJECTED';

export const GENDERS = ['MALE', 'FEMALE', 'OTHER', 'PREFER_NOT_TO_SAY'] as const;
export type Gender = (typeof GENDERS)[number];

export const GENDER_LABELS: Readonly<Record<Gender, string>> = {
  MALE: 'Male',
  FEMALE: 'Female',
  OTHER: 'Other',
  PREFER_NOT_TO_SAY: 'Prefer not to say',
};

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

/**
 * UserAdminController.CREATABLE — who may create whom.
 *
 * Must stay byte-for-byte equivalent to the map in UserAdminController. If the
 * two drift, the form offers a role the server refuses and the user meets a 403
 * after filling in the whole thing.
 *
 *   SUPER_ADMIN   unrestricted. The server skips the check entirely for them.
 *   CAMPUS_ADMIN  teaching staff and gate staff on their own campus. NOT
 *                 students - those come from Faculty, who know who is actually
 *                 in their class, or from bulk onboarding. NOT another Campus
 *                 Admin - appointing a peer is the Super Admin's decision.
 *   FACULTY       their students, and nobody else.
 *
 * VISITOR is granted to nobody: visitors self-register, and bulk onboarding
 * mints them through the internal API, which never reaches this endpoint.
 *
 * SUPER_ADMIN appears in no other role's list, so campus-level access can never
 * escalate to the platform.
 *
 * Transcribed here because the form previously offered every role to everyone
 * and DEFAULTED to STUDENT - so a Campus Admin opening "Add user" saw a form
 * pre-filled with a role the server then refused. A 403 the UI could have
 * prevented reads as a broken app rather than as a rule. Keep this identical to
 * UserAdminController.CREATABLE; if the two drift, that bug comes back.
 *
 * ORDER MATTERS - the form defaults to the first entry and the dropdown renders
 * in this order.
 */
export const CREATABLE_ROLES: Readonly<Record<Role, readonly Role[]>> = {
  SUPER_ADMIN: ROLES,
  CAMPUS_ADMIN: ['FACULTY', 'GUARD'],
  FACULTY: ['STUDENT'],
  STUDENT: [],
  VISITOR: [],
  GUARD: [],
};

/** What this person may actually pick in a "create account" form. */
export const creatableRolesFor = (actor: Role | null | undefined): readonly Role[] =>
  actor ? CREATABLE_ROLES[actor] : [];

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

export const ID_TYPES = ['AADHAAR', 'PAN', 'PASSPORT', 'VOTER_ID'] as const;
export type IdType = (typeof ID_TYPES)[number];

export const VISITOR_TYPES = [
  'GUEST', 'PARENT', 'VENDOR', 'CONTRACTOR', 'ALUMNI', 'CANDIDATE', 'OTHER',
] as const;
export type VisitorType = (typeof VISITOR_TYPES)[number];

export const PURPOSE_TYPES = [
  'MEETING', 'INTERVIEW', 'DELIVERY', 'MAINTENANCE', 'EVENT', 'ACADEMIC',
  'PERSONAL', 'OTHER',
] as const;
export type PurposeType = (typeof PURPOSE_TYPES)[number];

/** Wire values are enum names; these are what a human reads. */
export const ID_TYPE_LABELS: Readonly<Record<IdType, string>> = {
  AADHAAR: 'Aadhaar', PAN: 'PAN', PASSPORT: 'Passport', VOTER_ID: 'Voter ID',
};
export const VISITOR_TYPE_LABELS: Readonly<Record<VisitorType, string>> = {
  GUEST: 'Guest', PARENT: 'Parent', VENDOR: 'Vendor', CONTRACTOR: 'Contractor',
  ALUMNI: 'Alumni', CANDIDATE: 'Interview candidate', OTHER: 'Other',
};
export const PURPOSE_TYPE_LABELS: Readonly<Record<PurposeType, string>> = {
  MEETING: 'Meeting', INTERVIEW: 'Interview', DELIVERY: 'Delivery',
  MAINTENANCE: 'Maintenance or service', EVENT: 'Event', ACADEMIC: 'Academic',
  PERSONAL: 'Personal', OTHER: 'Other',
};
