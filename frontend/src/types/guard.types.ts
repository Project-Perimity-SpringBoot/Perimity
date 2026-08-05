import type { DenialReason, PassType, ScanResult, SessionState } from './enums';

export interface ScanResponse {
  result: ScanResult;
  /** Server-composed and written for a human. Render verbatim. */
  message: string;
  denialReason: DenialReason | null;
  passId: number | null;
  holderUserId: number | null;
  holderName: string | null;
  /** Behavior 2 lives here — a daily QR scanned during an event. */
  attributedEventId: number | null;
  /** ALWAYS null today: ScanService passes null explicitly. */
  eventName: string | null;
  gateId: number;
  gateName: string;
  scannedAt: string;
  /** Mongo ObjectId as a string. */
  entryLogId: string;
  /** Stable object-storage key. Not renderable; use holderPhotoUrl. */
  holderPhotoKey: string | null;
  /**
   * Short-lived signed link, minted per scan by user-service. Null is normal —
   * a visitor has no profile, and the server returns null rather than stalling
   * a queue if object storage is slow.
   */
  holderPhotoUrl: string | null;
}

export interface ScanSessionResponse {
  id: string;
  guardUserId: number;
  campusId: number;
  gateId: number;
  gateName: string;
  state: SessionState;
  startedAt: string;
  endedAt: string | null;
  totalScans: number;
  allowedCount: number;
  deniedCount: number;
}

export interface EntryLogResponse {
  id: string;
  campusId: number;
  gateId: number;
  gateName: string;
  guardUserId: number;
  sessionId: string;
  passId: number | null;
  holderUserId: number | null;
  holderName: string | null;
  passType: PassType | null;
  eventId: number | null;
  attributedEventId: number | null;
  eventAttributed: boolean;
  scanResult: ScanResult;
  denialReason: DenialReason | null;
  scannedAt: string;
  /** "yyyy-MM-dd", denormalised server-side for day grouping. */
  scanDate: string;
}

export interface EntryStatsResponse {
  campusId: number;
  from: string;
  to: string;
  allowedCount: number;
  amberCount: number;
  deniedCount: number;
  /** allowed + amber */
  entriesPermitted: number;
  totalScans: number;
}

export interface DayAttendance {
  scanDate: string;
  attendedCount: number;
  attendancePercent: number;
}

export interface EventAttendanceResponse {
  eventId: number;
  eventName: string | null;
  registeredCount: number;
  uniqueAttendeeCount: number;
  neverShowedCount: number;
  days: DayAttendance[];
}

/* ── Requests ── */

/**
 * gateId, campusId and scannedAt are deliberately absent. They come from the
 * guard's open session and the server clock — that is what makes the entry log
 * evidence rather than a claim.
 */
export interface ScanRequest {
  token: string;
  deviceInfo?: Record<string, string | number | boolean | null>;
}

export interface ScanSessionStartRequest {
  /*
   * campusId is NOT sent, and must not be added back.
   *
   * ScanSessionStartDto dropped it deliberately. A client-supplied campus let a
   * guard open a shift naming ANY campus — and because every scan inherits its
   * campus from the open session, that shift could then admit people against
   * another campus's passes and write entry logs into another campus's
   * register. Writing into another tenant's evidence, not just reading it.
   *
   * The server takes it from the verified JWT instead.
   */
  gateId: number;
  gateName: string;
  deviceInfo?: Record<string, string | number | boolean | null>;
}

export interface EntryLogFilterRequest {
  campusId: number;
  from: string;
  to: string;
  /** Range may not exceed 90 days. */
  scanResult?: ScanResult | null;
}

export interface EventAttendanceQuery {
  from: string;
  to: string;
  eventName?: string;
  /** Supply from the gatepass attendance-summary call or every percent is zero. */
  registeredCount: number;
}

/** DeviceInfoRules: ≤10 entries, key ≤40, value ≤200, no control characters, flat. */
export const DEVICE_INFO_LIMITS = {
  maxEntries: 10,
  maxKeyLength: 40,
  maxValueLength: 200,
} as const;
