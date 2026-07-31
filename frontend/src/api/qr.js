import { qrApi } from './client';

/**
 * qr-service.
 *
 * ⚠ qr-service has NO CORS configuration (no SecurityConfig, no
 * WebMvcConfigurer). Every call below will be blocked by the browser until
 * that is added — see `backend-fix/QrCorsConfig.java` in this zip. The code
 * here is correct; the server has to allow the origin.
 */
export const qr = {
  /** What the Bulk Progress screen polls. Generation AND delivery counts. */
  batchProgress: (batchId) => qrApi.get(`/api/qr/jobs/batch/${batchId}/progress`),
  jobStatus:     (jobId)   => qrApi.get(`/api/qr/jobs/${jobId}/status`),
  /** The QR record for one pass — object keys, not image bytes. */
  forPass:       (passId)  => qrApi.get(`/api/qr/${passId}`),
};
