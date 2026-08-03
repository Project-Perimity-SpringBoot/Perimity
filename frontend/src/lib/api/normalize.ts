import type { AxiosResponse } from 'axios';
import type { DownloadedFile, Paged } from '@/types/api';
import type { ApiResponse, PageResponse } from '@/types/wire';
import { ApiError, MalformedResponseError, parseFieldErrors } from './errors';

/**
 * The single normalisation point. Six wire shapes enter; ordinary TypeScript
 * values leave. `ApiResponse` must not appear anywhere outside src/lib/api/.
 */

function isEnvelope(value: unknown): value is ApiResponse<unknown> {
  return typeof value === 'object' && value !== null && 'success' in value;
}

/** Shape 1 — ApiResponse<T>. */
export function unwrap<T>(raw: unknown): T {
  if (!isEnvelope(raw)) {
    throw new MalformedResponseError(
      'Response was not an ApiResponse envelope. The backend contract changed.',
    );
  }
  const envelope = raw as ApiResponse<T>;

  // Defensive: a 2xx carrying success:false should not exist, but if a handler
  // ever returns ApiResponse.fail without a status, silence would be worse.
  if (!envelope.success) {
    const { fieldErrors, formErrors } = parseFieldErrors(envelope.errors);
    throw new ApiError({
      status: 200,
      message: envelope.message || 'The request was not successful',
      fieldErrors,
      formErrors,
    });
  }

  // Void endpoints answer with data:null. That is success, not absence.
  return (envelope.data ?? undefined) as T;
}

/** Shape 2 — ApiResponse<PageResponse<T>> → the flat Paged<T> the UI uses. */
export function unwrapPage<T>(raw: unknown): Paged<T> {
  const page = unwrap<PageResponse<T>>(raw);
  if (!page || !Array.isArray(page.content)) {
    throw new MalformedResponseError('Expected a PageResponse body');
  }
  return {
    items: page.content,
    page: page.page,
    size: page.size,
    total: page.totalElements,
    totalPages: page.totalPages,
    isFirst: page.first,
    isLast: page.last,
    hasNext: !page.last,
    hasPrevious: !page.first,
  };
}

/** Shape 3 — ApiResponse<T[]>. Always an array, never null. */
export function unwrapList<T>(raw: unknown): T[] {
  const data = unwrap<T[] | null>(raw);
  return Array.isArray(data) ? data : [];
}

/**
 * Shape 4 — ApiResponse<Map<String, V>>. The keys are conventions rather than
 * types, so the caller names the one it wants. See SCALAR_KEYS.
 */
export function unwrapScalar<V>(raw: unknown, key: string): V {
  const data = unwrap<Record<string, V>>(raw);
  if (!data || !(key in data)) {
    throw new MalformedResponseError(`Expected key "${key}" in the response map`);
  }
  return data[key] as V;
}

/** Shape 5 — raw bytes plus the server's own filename. */
export function unwrapFile(response: AxiosResponse<Blob>): DownloadedFile {
  const disposition = response.headers['content-disposition'];
  return {
    blob: response.data,
    filename: filenameFromDisposition(
      typeof disposition === 'string' ? disposition : undefined,
    ),
    contentType:
      response.data.type ||
      (typeof response.headers['content-type'] === 'string'
        ? response.headers['content-type']
        : 'application/octet-stream'),
  };
}

function filenameFromDisposition(disposition: string | undefined): string {
  if (!disposition) return 'download';
  const utf8 = /filename\*=UTF-8''([^;]+)/i.exec(disposition);
  if (utf8?.[1]) return decodeURIComponent(utf8[1]);
  const plain = /filename="?([^";]+)"?/i.exec(disposition);
  return plain?.[1] ?? 'download';
}

/**
 * The documented keys for shape 4. Declared once so no call site inlines a
 * string. /passes/count is the trap: it keys on the enum NAME, so a naive
 * `data.count` silently yields undefined and the dashboard shows nothing.
 */
export const SCALAR_KEYS = {
  /** GET /api/gatepass/passes/count?status=ACTIVE → { "ACTIVE": 1284 } */
  passCount: (status: string): string => status,
  /** GET /api/gatepass/visitor-requests/pending-count → { pending } */
  pendingRequests: 'pending',
  /** GET /api/auth/blocklist/count → { blocked } */
  blockedCount: 'blocked',
  /** GET /api/user/{students|faculty}/count → { count } */
  profileCount: 'count',
  /** GET /api/gatepass/bulk/{id}/errors, GET /api/campus/.../logo → { url } */
  downloadUrl: 'url',
} as const;
