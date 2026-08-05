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

/**
 * Does this presigned URL need our Authorization header?
 *
 * ==========================================================================
 * RELATIVE MEANS OURS. ABSOLUTE MEANS ALREADY SIGNED.
 * ==========================================================================
 * LocalFileStorageService.presignedReadUrl returns "/api/user/storage/local/{key}"
 * — a path. It is called "presigned" for symmetry with the S3 implementation,
 * but it carries no signature at all: LocalStorageController sits behind the
 * JWT filter, deliberately, because that directory holds people's photographs
 * and identity documents and keys travel in ordinary API responses.
 *
 * S3's presigner returns a full https://…amazonaws.com/… URL that carries its
 * own signature, needs no token, and must NOT be fetched through our client —
 * that would send the user's bearer token to Amazon, and would fail CORS
 * before it got there.
 *
 * So the two cases are exactly relative vs absolute. Comparing origins would
 * be a more fragile way of asking the same question.
 *
 * This lives here rather than in the one component that first needed it,
 * because a rule with two copies is a rule that will eventually have two
 * different answers.
 */
export function needsToken(url: string): boolean {
  return !/^https?:\/\//i.test(url);
}

/**
 * Open a blank tab NOW, while the click is still being handled.
 *
 * ==========================================================================
 * WHY THE TAB IS OPENED BEFORE THERE IS ANYTHING TO PUT IN IT
 * ==========================================================================
 * window.open is only permitted while a user gesture is being handled. A
 * presigned URL has to be fetched first — it is minted per request and
 * expires — so calling window.open in the success callback runs after the
 * gesture is spent, and Chrome, Safari and Firefox all block it.
 *
 * It appears to work on localhost, which most blockers allowlist, and then
 * fails silently for real users: no error, no tab, nothing to report.
 *
 * ==========================================================================
 * WHY 'noopener' IS NOT PASSED HERE
 * ==========================================================================
 * Per the HTML spec, window.open RETURNS NULL when noopener is set — the
 * whole point of the flag is that the two windows cannot reference each
 * other, and that includes us referencing the one we just opened. So
 * `window.open('', '_blank', 'noopener')` opens a blank tab we then have no
 * way to navigate, and every caller silently falls through to whatever its
 * null branch does. That is a real bug this codebase shipped: the fallback
 * navigated the current tab away from the application and left the blank one
 * stranded.
 *
 * Severing tab.opener directly is the same protection with a usable return
 * value. The referrer is no longer suppressed; the only cross-origin
 * destination here is a presigned S3 link, which does not care.
 */
export function openBlankTab(): Window | null {
  const tab = window.open('', '_blank');
  if (tab) tab.opener = null;
  return tab;
}
