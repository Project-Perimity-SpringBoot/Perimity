import type { ServiceName } from '../api/serviceName';
// Needed so "who am I" endpoints answer for the CURRENT token rather than a
// fixed fixture. No cycle: tokenStore reaches claims, never back to the client.
import { tokenStore } from '../auth/tokenStore';
import {
  MOCK_BULK_BATCH, MOCK_BULK_SUMMARY, MOCK_CAMPUSES, MOCK_CAMPUS_STATS, MOCK_DEPARTMENTS,
  MOCK_ENTRY_LOGS, MOCK_EVENTS, MOCK_GATES, MOCK_PASSES, MOCK_REQUESTS, MOCK_SESSION,
  MOCK_STUDENT_PROFILE, MOCK_USERS, NOW,
} from './fixtures';

/**
 * Path → response, per service.
 *
 * EVERY HANDLER RETURNS THE WIRE SHAPE, envelope and all. Mocks that return a
 * bare object would bypass normalize.ts, and the first thing to break on the
 * real backend would be the layer the mocks were meant to exercise.
 */

type Json = unknown;
export interface MockRequest {
  method: string;
  /** Path with the baseURL already stripped, query string removed. */
  path: string;
  body: unknown;
  params: Record<string, unknown>;
}
export type MockHandler = (request: MockRequest) => Json;

const ok = (data: unknown, message = 'OK'): Json => ({ success: true, message, data, errors: null });

/**
 * The password every fixture account uses in mock mode.
 * Anything else is refused, so the wrong-credentials message is reachable
 * without a backend running.
 */
export const MOCK_PASSWORD = 'Perimity@2026';

/** Thrown as a real 401 so the interceptor produces an UnauthorizedError. */
class MockHttpError extends Error {
  constructor(readonly status: number, readonly payload: unknown) {
    super('mock http error');
  }
}
export { MockHttpError };

const fail = (status: number, message: string): never => {
  throw new MockHttpError(status, { success: false, message, data: null, errors: [] });
};
const page = (items: unknown[], size = 20): Json =>
  ok({
    content: items, page: 0, size,
    totalElements: items.length,
    totalPages: Math.max(1, Math.ceil(items.length / size)),
    first: true, last: true,
  });

/** Which fixture user a mock login returns, chosen by the email typed in. */
function userForEmail(email: unknown): typeof MOCK_USERS[string] {
  const value = String(email ?? '').toLowerCase();
  if (value.includes('platform')) return MOCK_USERS['superAdmin'] as typeof MOCK_USERS[string];
  if (value.includes('verma') || value.includes('admin')) return MOCK_USERS['campusAdmin'] as typeof MOCK_USERS[string];
  if (value.includes('rao') || value.includes('faculty')) return MOCK_USERS['faculty'] as typeof MOCK_USERS[string];
  if (value.includes('singh') || value.includes('guard')) return MOCK_USERS['guard'] as typeof MOCK_USERS[string];
  if (value.includes('mehta') || value.includes('visitor')) return MOCK_USERS['visitor'] as typeof MOCK_USERS[string];
  return MOCK_USERS['student'] as typeof MOCK_USERS[string];
}

/**
 * An unsigned three-part string shaped like a JWT.
 *
 * claims.ts decodes the payload and never verifies the signature client-side —
 * verification is the server's job — so this is enough to exercise the real
 * decode path rather than stubbing around it.
 */
function fakeJwt(user: typeof MOCK_USERS[string]): string {
  const header = { alg: 'HS256', typ: 'JWT' };
  const issued = Math.floor(Date.now() / 1000);
  const payload = {
    jti: 'mock-' + String(user.id), sub: String(user.id),
    email: user.email, name: user.name, role: user.role, campusId: user.campusId,
    iss: 'perimity-auth', iat: issued, exp: issued + 24 * 60 * 60,
  };
  const encode = (value: unknown): string =>
    btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${encode(header)}.${encode(payload)}.mock-signature-not-verified-client-side`;
}

const authHandlers: Record<string, MockHandler> = {
  'POST /api/auth/login': ({ body }) => {
    const { email, password } = (body ?? {}) as { email?: string; password?: string };

    // One generic message for unknown email, wrong password and inactive
    // account — matching auth-service, which deliberately does not reveal
    // which of the three it was.
    const known = Object.values(MOCK_USERS).some(
      (u) => u.email.toLowerCase() === String(email ?? '').toLowerCase(),
    );
    if (!known || password !== MOCK_PASSWORD) {
      fail(401, 'Invalid email or password');
    }

    const user = userForEmail(email);
    return ok({
      token: fakeJwt(user), tokenType: 'Bearer',
      expiresAt: '2026-08-03T09:38:00', mustChangePassword: false, user,
    }, 'Signed in');
  },
  'POST /api/auth/otp/request': ({ body }) =>
    ok({
      maskedEmail: String((body as { email?: string })?.email ?? '').replace(/^(.).*(@.*)$/, '$1****$2'),
      purpose: 'LOGIN', expiresAt: '2026-08-02T09:48:00', attemptsAllowed: 5,
    }),
  'POST /api/auth/otp/verify': ({ body }) => {
    const user = userForEmail((body as { email?: string })?.email);
    return ok({
      token: fakeJwt(user), tokenType: 'Bearer',
      expiresAt: '2026-08-03T09:38:00', mustChangePassword: false, user,
    }, 'Signed in');
  },
  /*
   * Answers for the CURRENT token. Returning a fixed user here meant signing in
   * as one role and then another left the shell showing the wrong person — the
   * routing was right and the displayed identity was not.
   */
  'GET /api/auth/me': () => {
    const id = tokenStore.identity()?.userId;
    const match = Object.values(MOCK_USERS).find((u) => u.id === id);
    return ok(match ?? MOCK_USERS['student']);
  },
  'POST /api/auth/logout': () => ok(null, 'Signed out'),
  'POST /api/auth/password/change': () => ok(null, 'Password changed'),
  'POST /api/auth/password/reset-request': () => ok(null, 'If that address is registered, a link has been sent'),
  'POST /api/auth/password/reset-confirm': () => ok(null, 'Password reset'),
  'POST /api/auth/visitors/register': () => ok(MOCK_USERS['visitor'], 'Registered'),
  'GET /api/auth/users': () => page(Object.values(MOCK_USERS)),
  'GET /api/auth/blocklist': () => page([
    { id: 1, campusId: 1, email: 'blocked@example.com', phone: null,
      reason: 'Repeated no-show after approval', createdBy: 7, createdAt: '2026-06-02T10:00:00' },
  ]),
  'GET /api/auth/blocklist/count': () => ok({ blocked: 1 }),
  'GET /api/auth/audit': () => page([
    { id: 1, actorUserId: 42, actorRole: 'FACULTY', action: 'REQUEST_REJECTED',
      targetEntity: 'visitor-request:4188', campusId: 1, sourceIp: '10.4.2.88',
      details: 'Rejected with reason', createdAt: '2026-07-27T09:15:00' },
    { id: 2, actorUserId: 7, actorRole: 'CAMPUS_ADMIN', action: 'CAMPUS_CONFIG_CHANGED',
      targetEntity: 'config:repeat_entry_result', campusId: 1, sourceIp: '10.4.2.15',
      details: 'AMBER → GREEN', createdAt: '2026-07-26T11:02:00' },
  ], 50),
};

const gatepassHandlers: Record<string, MockHandler> = {
  'GET /api/gatepass/passes/mine': () => ok(MOCK_PASSES.filter((p) => p.holderUserId === 108)),
  'GET /api/gatepass/passes/mine/active': () => ok(MOCK_PASSES.filter((p) => p.scannable)),
  'GET /api/gatepass/passes/count': ({ params }) =>
    ok({ [String(params['status'] ?? 'ACTIVE')]: 1284 }),
  'GET /api/gatepass/visitor-requests': () => page(MOCK_REQUESTS),
  'GET /api/gatepass/visitor-requests/mine': () => page(MOCK_REQUESTS.filter((r) => r.status === 'PENDING')),
  'GET /api/gatepass/visitor-requests/my-history': () => ok(MOCK_REQUESTS),
  'GET /api/gatepass/visitor-requests/by-email': () => ok([]),
  'GET /api/gatepass/visitor-requests/pending-count': () => ok({ pending: 15 }),
  'POST /api/gatepass/visitor-requests': ({ body }) =>
    ok({ ...MOCK_REQUESTS[0], ...(body as object), id: 4200, status: 'PENDING' }, 'Request submitted'),
  'GET /api/gatepass/events': () => page(MOCK_EVENTS),
  'GET /api/gatepass/events/running': () => ok(MOCK_EVENTS.filter((e) => e.runningToday)),
  'POST /api/gatepass/bulk/validate': () => ok(MOCK_BULK_SUMMARY, 'Sheet validated'),
  'GET /api/gatepass/bulk': () => page([MOCK_BULK_BATCH], 10),
  'POST /api/gatepass/bulk/77/confirm': () => ok({ ...MOCK_BULK_BATCH, status: 'PROCESSING' }, 'Batch confirmed'),
  'GET /api/gatepass/bulk/77': () => ok(MOCK_BULK_BATCH),
  'GET /api/gatepass/bulk/77/errors': () => ok({ url: 'blob:mock-error-report' }),
};

const campusHandlers: Record<string, MockHandler> = {
  'GET /api/campus/campuses': () => ok(MOCK_CAMPUSES),
  'GET /api/campus/campuses/stats': () => ok(MOCK_CAMPUS_STATS),
  'GET /api/campus/campuses/1': () => ok(MOCK_CAMPUSES[0]),
  'GET /api/campus/campuses/1/gates': () => ok(MOCK_GATES),
  'GET /api/campus/campuses/1/config': () => ok([
    { id: 1, campusId: 1, configKey: 'visitor_approval_required', configValue: 'true', valueType: 'BOOLEAN', description: null, createdAt: NOW, updatedAt: null },
    { id: 2, campusId: 1, configKey: 'repeat_entry_result', configValue: 'AMBER', valueType: 'STRING', description: null, createdAt: NOW, updatedAt: null },
    { id: 3, campusId: 1, configKey: 'daily_pass_validity_days', configValue: '365', valueType: 'INTEGER', description: null, createdAt: NOW, updatedAt: null },
    { id: 4, campusId: 1, configKey: 'max_visitor_duration_days', configValue: '7', valueType: 'INTEGER', description: null, createdAt: NOW, updatedAt: null },
    { id: 5, campusId: 1, configKey: 'otp_expiry_minutes', configValue: '10', valueType: 'INTEGER', description: null, createdAt: NOW, updatedAt: null },
    { id: 6, campusId: 1, configKey: 'photo_required_for_pass', configValue: 'true', valueType: 'BOOLEAN', description: null, createdAt: NOW, updatedAt: null },
  ]),
};

const userHandlers: Record<string, MockHandler> = {
  'GET /api/user/students/me': () => ok(MOCK_STUDENT_PROFILE),
  'GET /api/user/students/count': () => ok({ count: 4210 }),
  'GET /api/user/faculty/count': () => ok({ count: 168 }),
  'GET /api/user/faculty': () => page([
    { id: 300, userId: 42, campusId: 1, departmentId: 1, departmentName: 'Computer Science',
      employeeId: 'F-1042', designation: 'Associate Professor', qualification: 'PhD',
      photoS3Key: null, createdAt: '2025-08-01T10:00:00', updatedAt: null },
  ]),
  'GET /api/user/departments': () => ok(MOCK_DEPARTMENTS),
  'GET /api/user/documents/me': () => ok([
    { id: 900, userId: 108, docType: 'ID_PROOF', s3Key: 'users/108/id.pdf', fileName: 'id-proof.pdf',
      mimeType: 'application/pdf', verified: true, verifiedBy: 7, verifiedAt: '2026-01-14T10:00:00',
      verificationRemarks: null, createdAt: '2026-01-13T10:00:00' },
  ]),
};

const guardHandlers: Record<string, MockHandler> = {
  'GET /api/guard/sessions/current': () => ok(MOCK_SESSION),
  'GET /api/guard/sessions/history': () => ok([{ ...MOCK_SESSION, state: 'CLOSED', endedAt: '2026-08-01T16:00:00' }]),
  'POST /api/guard/sessions': ({ body }) => ok({ ...MOCK_SESSION, ...(body as object) }, 'Shift started'),
  'POST /api/guard/scan': () => ok({
    result: 'ALLOWED', message: 'Welcome, Sneha Kulkarni', denialReason: null,
    passId: 20418, holderUserId: 108, holderName: 'Sneha Kulkarni',
    attributedEventId: 6, eventName: null, gateId: 2, gateName: 'Gate 2',
    scannedAt: NOW, entryLogId: '66ad2011', holderPhotoKey: null,
  }, 'Welcome, Sneha Kulkarni'),
  'POST /api/guard/entry-logs/search': () => page(MOCK_ENTRY_LOGS, 50),
  'POST /api/guard/entry-logs/stats': () => ok({
    campusId: 1, from: '2026-07-26T00:00:00', to: NOW,
    allowedCount: 289, amberCount: 23, deniedCount: 14,
    entriesPermitted: 312, totalScans: 326,
  }),
};

const qrHandlers: Record<string, MockHandler> = {
  'GET /api/qr/jobs/batch/77/progress': () => ok({
    batchId: 77, total: 580, queued: 268, processing: 0, done: 312, failed: 0,
    percentComplete: 53, finished: false,
    emailsSent: 297, emailsFailed: 3, emailsPending: 280,
  }),
};

export const HANDLERS: Record<ServiceName, Record<string, MockHandler>> = {
  auth: authHandlers,
  user: userHandlers,
  gatepass: gatepassHandlers,
  campus: campusHandlers,
  guard: guardHandlers,
  qr: qrHandlers,
};
