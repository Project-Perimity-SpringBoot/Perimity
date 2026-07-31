import { guardApi } from './client';

/** guard-service. Paths verified against ScanController / EntryLogController. */
export const guard = {
  /** The scan. `token` is the decoded QR payload, verbatim. */
  scan:        (token, deviceInfo) => guardApi.post('/api/guard/scan', { token, deviceInfo }),

  startSession: (campusId, gateId, gateName, deviceInfo) =>
    guardApi.post('/api/guard/sessions', { campusId, gateId, gateName, deviceInfo }),
  currentSession: ()          => guardApi.get('/api/guard/sessions/current'),
  openSessions:   ()          => guardApi.get('/api/guard/sessions/open'),
  sessionHistory: (params={}) => guardApi.get('/api/guard/sessions/history', { params }),
  endSession:     (id)        => guardApi.post(`/api/guard/sessions/${id}/end`),

  /**
   * Entry log search is a POST with a body, not a GET with query params —
   * the filter carries a date range the backend validates as a pair.
   * The 90-day cap is enforced server-side; the UI mirrors it so the user
   * finds out before the round trip.
   */
  searchEntries: (filter, page = 0, size = 20) =>
    guardApi.post('/api/guard/entry-logs/search', filter, { params: { page, size } }),
  entryStats:    (filter)     => guardApi.post('/api/guard/entry-logs/stats', filter),
  entriesForHolder: (userId, params = {}) =>
    guardApi.get(`/api/guard/entry-logs/holder/${userId}`, { params }),
  entriesForPass: (passId)    => guardApi.get(`/api/guard/entry-logs/pass/${passId}`),
  entriesForSession: (sessionId, params = {}) =>
    guardApi.get(`/api/guard/entry-logs/session/${sessionId}`, { params }),
  /**
   * The organiser attendance view.
   *
   * `from`, `to` and `registeredCount` are REQUIRED for a correct answer, and
   * the reason is worth knowing: guard-service holds entry logs but has no
   * idea how many passes were issued, so it cannot compute "never showed" on
   * its own. gatepass-service knows the registered count; guard-service knows
   * who turned up. The two endpoints were built to compose, and `registeredCount`
   * defaults to 0 server-side — so calling this without it returns a perfectly
   * well-formed response in which nobody ever failed to show up.
   *
   * Use `eventAttendanceFor()` below rather than calling this directly.
   */
  eventAttendance: (eventId, { from, to, eventName, registeredCount = 0 }) =>
    guardApi.get(`/api/guard/entry-logs/events/${eventId}/attendance`,
      { params: { from, to, eventName, registeredCount } }),
};

/** The three verdicts, exactly as ScanResult spells them. */
export const VERDICT = { ALLOWED: 'ALLOWED', AMBER: 'AMBER', DENIED: 'DENIED' };

/**
 * A guard should never see a bare enum. DenialReason values map to a sentence
 * the guard can act on — "which of these do I tell the person at the gate?"
 * The fallback matters: a new reason added backend-side must still render as
 * something, not as blank space next to a red screen.
 */
export const DENIAL_TEXT = {
  PASS_NOT_FOUND:     'No pass matches this code.',
  PASS_REVOKED:       'This pass was revoked.',
  PASS_PAUSED:        'This pass is paused. The holder must complete their profile.',
  PASS_EXPIRED:       'This pass has expired.',
  PASS_NOT_YET_VALID: 'This pass is not valid yet.',
  WRONG_CAMPUS:       'This pass is for a different campus.',
  INVALID_TOKEN:      'The code could not be read. Try again, or look them up by name.',
  TOKEN_EXPIRED:      'This code is stale. Ask them to refresh their pass.',
  NO_ACTIVE_SESSION:  'Your shift is not started. Pick a gate first.',
  EVENT_CANCELLED:    'The event this pass belongs to was cancelled.',
};
export const denialText = (reason) =>
  DENIAL_TEXT[reason] || (reason ? reason.replace(/_/g, ' ').toLowerCase() : 'Entry refused.');
