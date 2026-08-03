import type { AxiosInstance } from 'axios';
import type { DownloadedFile } from '@/types/api';
import { unwrapFile } from './normalize';

/**
 * Binary endpoints return raw bytes with Content-Disposition, not an
 * ApiResponse envelope: /bulk/template, /events/{id}/attendees.csv and the
 * dev /storage/local/** routes.
 */
export async function fetchFile(
  client: AxiosInstance,
  url: string,
  params?: Record<string, unknown>,
): Promise<DownloadedFile> {
  const response = await client.get<Blob>(url, {
    responseType: 'blob',
    ...(params ? { params } : {}),
  });
  return unwrapFile(response);
}

/** Trigger a browser save without leaving the SPA. */
export function saveFile(file: DownloadedFile): void {
  const href = URL.createObjectURL(file.blob);
  const anchor = document.createElement('a');
  anchor.href = href;
  anchor.download = file.filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  // Revoke on the next frame; revoking synchronously cancels the download in
  // Safari before it starts.
  requestAnimationFrame(() => URL.revokeObjectURL(href));
}
