import { ChevronLeft, ChevronRight } from 'lucide-react';
import { Button } from '@ui/index';
import type { Paged } from '@/types/api';

export function Pagination<T>({ page, onPageChange }: { page: Paged<T>; onPageChange: (n: number) => void }) {
  if (page.totalPages <= 1) return null;
  const from = page.page * page.size + 1;
  const to = Math.min(page.total, from + page.items.length - 1);

  return (
    <nav
      aria-label="Pagination"
      className="flex items-center justify-between gap-[var(--sp-4)] border-t border-[var(--border)] px-[var(--sp-4)] py-[var(--sp-3)]"
    >
      <p className="text-caption text-[var(--ink-500)] tabular-nums">
        {from}–{to} of {page.total}
      </p>
      <div className="flex items-center gap-[var(--sp-1)]">
        <Button
          variant="secondary"
          size="sm"
          disabled={!page.hasPrevious}
          onClick={() => onPageChange(page.page - 1)}
        >
          <ChevronLeft aria-hidden />
          Previous
        </Button>
        <span className="text-caption px-[var(--sp-2)] text-[var(--ink-500)] tabular-nums">
          Page {page.page + 1} of {page.totalPages}
        </span>
        <Button
          variant="secondary"
          size="sm"
          disabled={!page.hasNext}
          onClick={() => onPageChange(page.page + 1)}
        >
          Next
          <ChevronRight aria-hidden />
        </Button>
      </div>
    </nav>
  );
}
