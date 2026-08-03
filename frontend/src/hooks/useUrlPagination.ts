import { useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router';
import type { PageRequest } from '@/types/api';

/**
 * Pagination and filters live in the query string, not React state, so an
 * admin can send a colleague a link to exactly the rows they are looking at.
 */
export function useUrlPagination(defaultSize = 20) {
  const [params, setParams] = useSearchParams();

  const page = Number(params.get('page') ?? '0');
  const size = Number(params.get('size') ?? String(defaultSize));

  const request = useMemo<PageRequest>(
    () => ({ page: Number.isFinite(page) ? page : 0, size: Number.isFinite(size) ? size : defaultSize }),
    [page, size, defaultSize],
  );

  const setPage = useCallback(
    (next: number) => {
      setParams((prev) => {
        const copy = new URLSearchParams(prev);
        copy.set('page', String(Math.max(0, next)));
        return copy;
      });
    },
    [setParams],
  );

  const setFilter = useCallback(
    (key: string, value: string | null) => {
      setParams((prev) => {
        const copy = new URLSearchParams(prev);
        if (value === null || value === '') copy.delete(key);
        else copy.set(key, value);
        copy.set('page', '0'); // a new filter always returns to the first page
        return copy;
      });
    },
    [setParams],
  );

  return { request, page: request.page ?? 0, size: request.size ?? defaultSize, setPage, setFilter, params };
}
