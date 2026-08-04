/**
 * PUBLIC. The shapes the UI is allowed to know about.
 * The wire envelopes live in types/wire.ts and never reach a component.
 */

/** A page, flat and identical whichever service produced it. */
export interface Paged<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
  isFirst: boolean;
  isLast: boolean;
  hasNext: boolean;
  hasPrevious: boolean;
}

export interface PageRequest {
  page?: number;
  size?: number;
  /**
   * Deliberately omitted from most calls. Several endpoints fix their order via
   * @PageableDefault or a repository method ending in OrderByIdDesc; sending a
   * Sort as well makes Spring Data emit two ORDER BY clauses. Contract §2.11.
   */
  sort?: string;
}

export interface DownloadedFile {
  blob: Blob;
  filename: string;
  contentType: string;
}

/** An empty page, for optimistic rendering before the first fetch resolves. */
export function emptyPage<T>(size = 20): Paged<T> {
  return {
    items: [], page: 0, size, total: 0, totalPages: 0,
    isFirst: true, isLast: true, hasNext: false, hasPrevious: false,
  };
}
