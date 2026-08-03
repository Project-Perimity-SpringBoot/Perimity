import type { AuditAction, OtpPurpose, Role } from './enums';

/* ── Responses ── */

/** auth.dto.response.UserResponse — no passwordHash, no failedLoginCount. */
export interface UserResponse {
  id: number;
  email: string;
  name: string;
  phone: string | null;
  role: Role;
  /** null for SUPER_ADMIN. Its absence is why B5 exists. */
  campusId: number | null;
  active: boolean;
  /** Derived server-side from lockedUntil; the raw counters are withheld. */
  locked: boolean;
  mustChangePassword: boolean;
  lastLoginAt: string | null;
  createdAt: string;
  updatedAt: string | null;
}

export interface AuthResponse {
  token: string;
  tokenType: 'Bearer';
  expiresAt: string;
  mustChangePassword: boolean;
  user: UserResponse | null;
}

/** Never contains the code. The address is masked instead. */
export interface OtpChallengeResponse {
  maskedEmail: string | null;
  purpose: OtpPurpose;
  expiresAt: string;
  attemptsAllowed: number;
}

export interface BlocklistEntryResponse {
  id: number;
  campusId: number;
  email: string | null;
  phone: string | null;
  reason: string;
  createdBy: number | null;
  createdAt: string;
}

export interface AuditLogResponse {
  id: number;
  actorUserId: number | null;
  actorRole: Role | null;
  action: AuditAction;
  targetEntity: string | null;
  campusId: number | null;
  sourceIp: string | null;
  /** Free text. NOT a structured before/after diff — see Contract §7.8 #20. */
  details: string | null;
  createdAt: string;
}

/* ── Requests ── */

export interface LoginRequest {
  email: string;
  /** 1–72. The password policy is deliberately NOT applied on login. */
  password: string;
}

export interface OtpRequest {
  email: string;
  purpose: OtpPurpose;
  campusId?: number | null;
}

export interface OtpVerifyRequest {
  email: string;
  purpose: OtpPurpose;
  code: string;
}

export interface VisitorRegistrationRequest {
  email: string;
  name: string;
  phone?: string | null;
  campusId: number;
}

export interface PasswordChangeRequest {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

export interface PasswordResetRequest {
  email: string;
}

export interface PasswordResetConfirmRequest {
  /** SHA-256 hex, from the emailed link. */
  token: string;
  newPassword: string;
  confirmPassword: string;
}

export interface UserCreateRequest {
  email: string;
  name: string;
  phone?: string | null;
  role: Role;
  /** Required unless role is SUPER_ADMIN; must be absent when it is. */
  campusId?: number | null;
  /** Required for every role except VISITOR; must be absent for VISITOR. */
  temporaryPassword?: string | null;
}

/** email, role and campusId are immutable. Do not send them. */
export interface UserUpdateRequest {
  name: string;
  phone?: string | null;
}

export interface UserStatusUpdateRequest {
  active: boolean;
  reason: string;
  /** Not stripped server-side — the client sends its own userId. */
  changedBy: number;
}

export interface BlocklistCreateRequest {
  campusId: number;
  /** At least one of email or phone must be present. */
  email?: string | null;
  phone?: string | null;
  reason: string;
  createdBy: number;
}
