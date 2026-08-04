import { QueryClient } from '@tanstack/react-query';
import { ApiError } from '@lib/api/errors';

/**
 * A 4xx will still be a 4xx on the third attempt. Retrying a 403 wastes the
 * user's time and hides the real answer; retrying a 429 makes it worse.
 */
function shouldRetry(failureCount: number, error: unknown): boolean {
  if (error instanceof ApiError && error.status >= 400 && error.status < 500) return false;
  return failureCount < 2;
}

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      gcTime: 5 * 60_000,
      retry: shouldRetry,
      retryDelay: (attempt) => Math.min(1000 * 2 ** attempt, 8000),
      refetchOnWindowFocus: true,
      refetchOnReconnect: true,
    },
    mutations: {
      retry: false,
    },
  },
});

/** Polling intervals, each with a reason. */
export const POLL = {
  /** BulkUploadController: "Screen 10 polls this every two seconds." */
  bulkProgressMs: 2_000,
  /** QR generation is async and there is no push channel anywhere. */
  passActivationMs: 3_000,
  passActivationMaxAttempts: 20,
  /** Cheap and low-stakes. */
  pendingCountMs: 60_000,
} as const;
