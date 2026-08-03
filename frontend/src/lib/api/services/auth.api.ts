import { authClient } from '../client';
import { unwrap, unwrapList, unwrapPage, unwrapScalar, SCALAR_KEYS } from '../normalize';
import type { Paged, PageRequest } from '@/types/api';
import type { Role } from '@/types/enums';
import type {
  AuditLogResponse, AuthResponse, BlocklistCreateRequest, BlocklistEntryResponse,
  LoginRequest, OtpChallengeResponse, OtpRequest, OtpVerifyRequest,
  PasswordChangeRequest, PasswordResetConfirmRequest, PasswordResetRequest,
  UserCreateRequest, UserResponse, UserStatusUpdateRequest, UserUpdateRequest,
} from '@/types/auth.types';

const BASE = '/api/auth';

/**
 * Reference implementation of the service-module pattern: every function takes
 * and returns plain domain types. The word ApiResponse appears nowhere.
 */
export const authApi = {
  /* ── public ── */

  async login(body: LoginRequest): Promise<AuthResponse> {
    const { data } = await authClient.post<unknown>(`${BASE}/login`, body);
    return unwrap<AuthResponse>(data);
  },

  /**
   * Always resolves, even for an unknown address and even for a password-only
   * role — the backend refuses those silently to prevent enumeration. The UI
   * must therefore never offer this path to an admin or a guard.
   */
  async requestOtp(body: OtpRequest): Promise<OtpChallengeResponse> {
    const { data } = await authClient.post<unknown>(`${BASE}/otp/request`, body);
    return unwrap<OtpChallengeResponse>(data);
  },

  async verifyOtp(body: OtpVerifyRequest): Promise<AuthResponse> {
    const { data } = await authClient.post<unknown>(`${BASE}/otp/verify`, body);
    return unwrap<AuthResponse>(data);
  },

  async registerVisitor(body: {
    email: string; name: string; phone?: string | null; campusId: number;
  }): Promise<UserResponse> {
    const { data } = await authClient.post<unknown>(`${BASE}/visitors/register`, body);
    return unwrap<UserResponse>(data);
  },

  async requestPasswordReset(body: PasswordResetRequest): Promise<void> {
    const { data } = await authClient.post<unknown>(`${BASE}/password/reset-request`, body);
    unwrap<void>(data);
  },

  async confirmPasswordReset(body: PasswordResetConfirmRequest): Promise<void> {
    const { data } = await authClient.post<unknown>(`${BASE}/password/reset-confirm`, body);
    unwrap<void>(data);
  },

  /* ── session ── */

  async me(): Promise<UserResponse> {
    const { data } = await authClient.get<unknown>(`${BASE}/me`);
    return unwrap<UserResponse>(data);
  },

  /** Idempotent server-side; succeeds even for an already-expired token. */
  async logout(): Promise<void> {
    const { data } = await authClient.post<unknown>(`${BASE}/logout`);
    unwrap<void>(data);
  },

  async changePassword(body: PasswordChangeRequest): Promise<void> {
    const { data } = await authClient.post<unknown>(`${BASE}/password/change`, body);
    unwrap<void>(data);
  },

  /* ── user administration ── */

  async createUser(body: UserCreateRequest): Promise<UserResponse> {
    const { data } = await authClient.post<unknown>(`${BASE}/users`, body);
    return unwrap<UserResponse>(data);
  },

  async updateUser(id: number, body: UserUpdateRequest): Promise<UserResponse> {
    const { data } = await authClient.put<unknown>(`${BASE}/users/${id}`, body);
    return unwrap<UserResponse>(data);
  },

  async changeUserStatus(id: number, body: UserStatusUpdateRequest): Promise<UserResponse> {
    const { data } = await authClient.patch<unknown>(`${BASE}/users/${id}/status`, body);
    return unwrap<UserResponse>(data);
  },

  async getUser(id: number): Promise<UserResponse> {
    const { data } = await authClient.get<unknown>(`${BASE}/users/${id}`);
    return unwrap<UserResponse>(data);
  },

  /** No `sort` — @PageableDefault fixes the order. Contract §2.11. */
  async listUsers(
    params: PageRequest & { role?: Role } = {},
  ): Promise<Paged<UserResponse>> {
    const { data } = await authClient.get<unknown>(`${BASE}/users`, { params });
    return unwrapPage<UserResponse>(data);
  },

  /* ── blocklist (SUPER_ADMIN, CAMPUS_ADMIN only) ── */

  async addToBlocklist(body: BlocklistCreateRequest): Promise<BlocklistEntryResponse> {
    const { data } = await authClient.post<unknown>(`${BASE}/blocklist`, body);
    return unwrap<BlocklistEntryResponse>(data);
  },

  async removeFromBlocklist(id: number): Promise<void> {
    const { data } = await authClient.delete<unknown>(`${BASE}/blocklist/${id}`);
    unwrap<void>(data);
  },

  async listBlocklist(
    params: PageRequest & { email?: string } = {},
  ): Promise<Paged<BlocklistEntryResponse>> {
    const { data } = await authClient.get<unknown>(`${BASE}/blocklist`, { params });
    return unwrapPage<BlocklistEntryResponse>(data);
  },

  async blocklistCount(): Promise<number> {
    const { data } = await authClient.get<unknown>(`${BASE}/blocklist/count`);
    return unwrapScalar<number>(data, SCALAR_KEYS.blockedCount);
  },

  /* ── audit (SUPER_ADMIN, CAMPUS_ADMIN only) ── */

  async listAudit(
    params: PageRequest & { action?: string } = {},
  ): Promise<Paged<AuditLogResponse>> {
    const { data } = await authClient.get<unknown>(`${BASE}/audit`, { params });
    return unwrapPage<AuditLogResponse>(data);
  },

  async auditByRange(
    from: string, to: string, params: PageRequest = {},
  ): Promise<Paged<AuditLogResponse>> {
    const { data } = await authClient.get<unknown>(`${BASE}/audit/range`, {
      params: { from, to, ...params },
    });
    return unwrapPage<AuditLogResponse>(data);
  },

  async auditByActor(
    actorUserId: number, params: PageRequest = {},
  ): Promise<Paged<AuditLogResponse>> {
    const { data } = await authClient.get<unknown>(`${BASE}/audit/actor/${actorUserId}`, { params });
    return unwrapPage<AuditLogResponse>(data);
  },

  /** Health probe. Used by the Phase 2 exit check. */
  async ping(): Promise<Record<string, string>> {
    const { data } = await authClient.get<unknown>(`${BASE}/ping`);
    return unwrap<Record<string, string>>(data);
  },
};

/** Kept exported so the list-shape helper is exercised and typed. */
export type AuditList = ReturnType<typeof unwrapList<AuditLogResponse>>;
