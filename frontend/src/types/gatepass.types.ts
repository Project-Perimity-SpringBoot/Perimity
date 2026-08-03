import type { BatchStatus, PassStatus, PassType, RequestStatus, RequestableStatus } from './enums';

/* ── Responses ── */

export interface GatePassResponse {
  id: number;
  holderUserId: number;
  holderName: string;
  campusId: number;
  visitorRequestId: number | null;
  passType: PassType;
  eventId: number | null;
  /** Populated only for EVENT passes, via an extra read. */
  eventName: string | null;
  validFrom: string;
  /** null means a standing DAILY pass with no end date. */
  validTo: string | null;
  status: PassStatus;
  /** === (status === 'ACTIVE'). Server-computed; prefer it over recomputing. */
  scannable: boolean;
  revokedReason: string | null;
  revokedBy: number | null;
  revokedAt: string | null;
  pausedReason: string | null;
  /** Storage KEY, not a URL. No resolver exists yet — blocker B1. */
  qrKey: string | null;
  pdfKey: string | null;
  createdAt: string;
  updatedAt: string | null;
}

export interface VisitorRequestResponse {
  id: number;
  campusId: number;
  visitorName: string;
  visitorEmail: string;
  visitorPhone: string | null;
  purpose: string;
  hostUserId: number;
  eventId: number | null;
  visitFrom: string;
  visitTo: string;
  /** Currently never true — blocker B2. Approval is impossible until it is. */
  otpVerified: boolean;
  visitorUserId: number | null;
  status: RequestStatus;
  /** null on auto-approval, by design — nobody reviewed it. */
  reviewedBy: number | null;
  reviewedAt: string | null;
  rejectReason: string | null;
  createdAt: string;
  updatedAt: string | null;
}

export interface EventResponse {
  id: number;
  campusId: number;
  name: string;
  description: string | null;
  validFrom: string;
  validTo: string;
  createdBy: number | null;
  cancelled: boolean;
  cancelledAt: string | null;
  /** Computed server-side against the server's today. */
  runningToday: boolean;
  issuedPassCount: number;
  createdAt: string;
  updatedAt: string | null;
}

export interface EventStatusCount {
  status: string;
  count: number;
}

export interface EventAttendanceSummaryResponse {
  eventId: number;
  eventName: string;
  validFrom: string;
  validTo: string;
  cancelled: boolean;
  totalPasses: number;
  registeredCount: number;
  registeredByStatus: EventStatusCount[];
  eventDays: string[];
}

export interface RowErrorResponse {
  rowNumber: number;
  email: string | null;
  reason: string;
}

export interface BulkValidationSummaryResponse {
  batchId: number;
  totalRows: number;
  validRows: number;
  invalidRows: number;
  errors: RowErrorResponse[];
  errorReportKey: string | null;
  /** validRows > 0 */
  awaitingConfirmation: boolean;
}

export interface BulkUploadBatchResponse {
  id: number;
  campusId: number;
  uploadedBy: number;
  /** DAILY = student batch, EVENT = event visitor batch. There is no BatchType enum. */
  passType: PassType;
  eventId: number | null;
  objectKey: string | null;
  originalFilename: string;
  status: BatchStatus;
  totalRows: number;
  validRows: number;
  invalidRows: number;
  processedRows: number;
  /** 0–100, computed server-side. Do not recompute. */
  percentComplete: number;
  errorReportKey: string | null;
  failureMessage: string | null;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string | null;
}

export interface BulkRetryResult {
  batchId: number;
  requeued: number;
  message: string;
}

/* ── Requests. campusId / createdBy / reviewedBy / changedBy are @JsonIgnore
 *    on the server and are absent here on purpose. Sending them is a no-op
 *    at best and a misunderstanding at worst. ── */

export interface GatePassCreateRequest {
  holderUserId: number;
  holderName: string;
  visitorRequestId?: number | null;
  passType: PassType;
  /** Required iff EVENT; forbidden when DAILY. */
  eventId?: number | null;
  validFrom: string;
  /** Required iff EVENT. */
  validTo?: string | null;
}

export interface GatePassStatusUpdateRequest {
  targetStatus: RequestableStatus;
  reason: string;
}

export interface VisitorRequestCreateRequest {
  visitorName: string;
  visitorEmail: string;
  visitorPhone?: string | null;
  purpose: string;
  hostUserId: number;
  eventId?: number | null;
  visitFrom: string;
  visitTo: string;
}

export interface VisitorRequestDecisionRequest {
  decision: Extract<RequestStatus, 'APPROVED' | 'REJECTED'>;
  /** Required when REJECTED. */
  rejectReason?: string | null;
}

export interface EventCreateRequest {
  name: string;
  description?: string | null;
  validFrom: string;
  validTo: string;
}

/** Same shape; validFrom loses its @FutureOrPresent on update. */
export type EventUpdateRequest = EventCreateRequest;

export interface BulkConfirmRequest {
  confirmedBy: number;
  /** @AssertTrue — literally must be true. */
  confirmed: true;
}

export interface BulkValidateRequest {
  file: File;
  passType: PassType;
  /** Required when passType is EVENT. */
  eventId?: number;
}
