import { qrClient } from '../client';
import { unwrap } from '../normalize';
import type { BatchProgressResponse, JobStatusResponse, QrRecordResponse } from '@/types/qr.types';

/**
 * qr-service has no Spring Security beyond an internal key filter on
 * /api/qr/internal/**, so these three reads accept any caller. The token is
 * still attached — costless, and nothing here changes the day a SecurityConfig
 * lands. Treat everything returned as non-sensitive display data and never make
 * an authorization decision from it.
 */
export const qrApi = {
  async jobStatus(jobId: number): Promise<JobStatusResponse> {
    const { data } = await qrClient.get<unknown>(`/api/qr/jobs/${jobId}/status`);
    return unwrap<JobStatusResponse>(data);
  },
  /** Three-stage breakdown behind the bulk progress screen. */
  async batchProgress(batchId: number): Promise<BatchProgressResponse> {
    const { data } = await qrClient.get<unknown>(`/api/qr/jobs/batch/${batchId}/progress`);
    return unwrap<BatchProgressResponse>(data);
  },
  async byPass(passId: number): Promise<QrRecordResponse> {
    const { data } = await qrClient.get<unknown>(`/api/qr/${passId}`);
    return unwrap<QrRecordResponse>(data);
  },
};
