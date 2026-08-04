/**
 * RESTRICTED. The raw envelopes the backend sends.
 *
 * Only src/lib/api/ may import from this file — enforced by ESLint. Everything
 * else consumes the normalised shapes in types/api.ts. If this import appears
 * in a feature, a component or a hook, the normalisation layer has been
 * bypassed and the UI now knows about a wire format it should not.
 */

/** Every JSON endpoint in all six services returns exactly this. */
export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T | null;
  errors: string[] | null;
}

/** Declared identically in auth, gatepass, guard and user. */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}
