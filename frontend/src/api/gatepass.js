import { gatepassApi } from './client';

/**
 * Every gatepass-service call the UI makes, in one file.
 *
 * Screens import functions, never URL strings. When Tushar renames a path, it
 * changes here once instead of in nine components — and a typo'd URL fails at
 * this boundary where it is obvious, rather than inside a render.
 *
 * Paths verified against gatepass-service controllers, 31 July snapshot.
 */
export const gatepass = {
  // ---- passes
  myPasses:      ()            => gatepassApi.get('/api/gatepass/passes/mine'),
  myActivePass:  ()            => gatepassApi.get('/api/gatepass/passes/mine/active'),
  pass:          (id)          => gatepassApi.get(`/api/gatepass/passes/${id}`),
  passesForHolder: (userId, p = {}) =>
    gatepassApi.get(`/api/gatepass/passes/holder/${userId}`, { params: p }),
  passesForEvent: (eventId, p = {}) =>
    gatepassApi.get(`/api/gatepass/passes/event/${eventId}`, { params: p }),
  /**
   * Returns a MAP KEYED BY STATUS — `{ "ACTIVE": 1284 }`, not `{ count: 1284 }`.
   * Unwrapped here so no screen has to know that, and so a status rename
   * breaks one line instead of four dashboards showing a silent zero.
   */
  passCount: async (status = 'ACTIVE') => {
    const map = await gatepassApi.get('/api/gatepass/passes/count', { params: { status } });
    return map?.[status] ?? 0;
  },

  /**
   * PAUSE / RESUME / REVOKE all go through one status endpoint.
   *
   * The field is `targetStatus`, NOT `status` — GatePassStatusUpdateDto spells
   * it that way and @NotNull rejects the request otherwise, with a 400 whose
   * message names a field the caller never sent.
   *
   * `reason` is @NotBlank for EVERY transition, including resume. Not an
   * oversight: a pass that was paused and quietly un-paused with no note is
   * indistinguishable from one that was never paused.
   */
  setPassStatus: (id, targetStatus, reason) =>
    gatepassApi.patch(`/api/gatepass/passes/${id}/status`, { targetStatus, reason }),
  republish:     (id)          => gatepassApi.post(`/api/gatepass/passes/${id}/republish`),

  // ---- visitor requests
  /**
   * NOTE: paged. The backend returns PageResponse<T>, so this resolves to
   * { content, page, size, totalElements, totalPages, first, last } — not a
   * bare array. Screens read `.content`.
   */
  visitorRequests: (status = 'PENDING', page = 0, size = 20) =>
    gatepassApi.get('/api/gatepass/visitor-requests', { params: { status, page, size } }),
  myQueue:       (status = 'PENDING', page = 0, size = 20) =>
    gatepassApi.get('/api/gatepass/visitor-requests/mine', { params: { status, page, size } }),
  myHistory:     ()            => gatepassApi.get('/api/gatepass/visitor-requests/my-history'),
  requestsByEmail: (email)     => gatepassApi.get('/api/gatepass/visitor-requests/by-email', { params: { email } }),
  /** Returns `{ pending: n }`. Unwrapped to the number. */
  pendingCount: async () => {
    const map = await gatepassApi.get('/api/gatepass/visitor-requests/pending-count');
    return map?.pending ?? 0;
  },
  visitorRequest: (id)         => gatepassApi.get(`/api/gatepass/visitor-requests/${id}`),
  passForRequest: (id)         => gatepassApi.get(`/api/gatepass/visitor-requests/${id}/pass`),
  submitRequest: (body)        => gatepassApi.post('/api/gatepass/visitor-requests', body),
  /**
   * Approve or reject.
   *
   * The body is `{ decision, rejectReason }` — an enum, not a boolean, and the
   * reason field is `rejectReason`, not `reason`. VisitorRequestDecisionDto
   * accepts APPROVED or REJECTED only; PENDING and CANCELLED are not decisions.
   *
   * `reviewedBy` is deliberately NOT sent. The backend overwrites it from the
   * token, because a body that names its own reviewer means one faculty member
   * could record an approval under another's name.
   */
  approve:  (id)          => gatepassApi.patch(`/api/gatepass/visitor-requests/${id}/decision`,
                              { decision: 'APPROVED' }),
  reject:   (id, rejectReason) => gatepassApi.patch(`/api/gatepass/visitor-requests/${id}/decision`,
                              { decision: 'REJECTED', rejectReason }),
  cancelRequest: (id)          => gatepassApi.patch(`/api/gatepass/visitor-requests/${id}/cancel`),

  // ---- events
  events:        (params = {}) => gatepassApi.get('/api/gatepass/events', { params }),
  runningEvents: ()            => gatepassApi.get('/api/gatepass/events/running'),
  event:         (id)          => gatepassApi.get(`/api/gatepass/events/${id}`),
  createEvent:   (body)        => gatepassApi.post('/api/gatepass/events', body),
  updateEvent:   (id, body)    => gatepassApi.put(`/api/gatepass/events/${id}`, body),
  cancelEvent:   (id)          => gatepassApi.patch(`/api/gatepass/events/${id}/cancel`),
  attendanceSummary: (id)      => gatepassApi.get(`/api/gatepass/events/${id}/attendance-summary`),
  /** Returns a URL, not bytes — the browser downloads it directly. */
  attendeesCsvUrl: (id)        => `${import.meta.env.VITE_GATEPASS_URL}/api/gatepass/events/${id}/attendees.csv`,

  // ---- bulk
  /** multipart. passType is STUDENT or EVENT; eventId only for EVENT. */
  validateSheet: (file, passType, eventId, onProgress) => {
    const form = new FormData();
    form.append('file', file);
    form.append('passType', passType);
    if (eventId) form.append('eventId', eventId);
    return gatepassApi.post('/api/gatepass/bulk/validate', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000,             // a 600-row sheet is not a 10-second request
      onUploadProgress: onProgress,
    });
  },
  confirmBatch:  (batchId)     => gatepassApi.post(`/api/gatepass/bulk/${batchId}/confirm`),
  retryBatch:    (batchId)     => gatepassApi.post(`/api/gatepass/bulk/${batchId}/retry`),
  batch:         (batchId)     => gatepassApi.get(`/api/gatepass/bulk/${batchId}`),
  batches:       (params = {}) => gatepassApi.get('/api/gatepass/bulk', { params }),
  /** Resolves to { url } — a short-lived link, not the file. */
  errorReport:   (batchId)     => gatepassApi.get(`/api/gatepass/bulk/${batchId}/errors`),
  templateUrl:   (passType = 'EVENT') =>
    `${import.meta.env.VITE_GATEPASS_URL}/api/gatepass/bulk/template?passType=${passType}`,
};
