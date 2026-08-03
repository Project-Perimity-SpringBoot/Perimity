import { qrClient } from '../client';
import { unwrap } from '../normalize';
import { fetchFile } from '../download';
import type { DownloadedFile } from '@/types/api';
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

  /**
   * The pass PDF and the QR image themselves — B1's fix.
   *
   * QrRecordResponse carries qrKey and pdfKey, but those are object-storage
   * keys, not URLs: the browser can do nothing with them, and making them
   * dereferenceable would mean a public bucket. These stream the bytes
   * instead, so storage stays private.
   *
   * Both are behind flags.passDownload until qr-service's endpoints merge.
   *
   * NOTE FOR ANY CALLER: these need the Bearer token, so a plain
   * <img src="/api/qr/1/image"> will 401 — an image tag sends no Authorization
   * header. Fetch through here and render the blob via an object URL.
   */
  image: (passId: number): Promise<DownloadedFile> =>
    fetchFile(qrClient, `/api/qr/${passId}/image`),

  pdf: (passId: number): Promise<DownloadedFile> =>
    fetchFile(qrClient, `/api/qr/${passId}/pdf`),
};
