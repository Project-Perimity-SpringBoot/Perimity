import type { EmailStatus, JobStatus } from './enums';

/**
 * qr-service has no Spring Security (blocker B4): these three reads accept any
 * caller. Treat everything here as non-sensitive display data and never make an
 * authorization decision from it.
 */

export interface BatchProgressResponse {
  batchId: number;
  total: number;
  queued: number;
  processing: number;
  done: number;
  failed: number;
  percentComplete: number;
  finished: boolean;
  emailsSent: number;
  emailsFailed: number;
  emailsPending: number;
}

export interface JobStatusResponse {
  jobId: number;
  passId: number;
  batchId: number | null;
  status: JobStatus;
  retryCount: number;
  errorMessage: string | null;
  startedAt: string | null;
  completedAt: string | null;
  emailStatus: EmailStatus;
  emailError: string | null;
  emailSentAt: string | null;
}

export interface QrRecordResponse {
  passId: number;
  campusId: number;
  qrKey: string | null;
  pdfKey: string | null;
  validFrom: string;
  validTo: string | null;
  active: boolean;
}
