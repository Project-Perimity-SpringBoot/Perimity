import { Search } from 'lucide-react';
import { Input } from '@ui/index';
import { cn } from '@lib/utils/cn';

export interface SearchFilterBarProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  /** Dropdowns and toggles, rendered to the right of the search box. */
  filters?: React.ReactNode;
  resultCount?: number;
  className?: string;
}

export function SearchFilterBar({
  value, onChange, placeholder = 'Search', filters, resultCount, className,
}: SearchFilterBarProps) {
  return (
    <div
      className={cn(
        'flex flex-wrap items-center gap-[var(--sp-3)] border-b border-[var(--border)] px-[var(--sp-4)] py-[var(--sp-3)]',
        className,
      )}
    >
      <div className="relative min-w-48 flex-1">
        <Search
          aria-hidden
          className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-[var(--ink-400)]"
        />
        <Input
          type="search"
          value={value}
          placeholder={placeholder}
          aria-label={placeholder}
          onChange={(e) => onChange(e.target.value)}
          className="pl-9"
        />
      </div>
      {filters}
      {resultCount !== undefined && (
        <p className="text-caption text-[var(--ink-500)] tabular-nums" aria-live="polite">
          {resultCount} {resultCount === 1 ? 'result' : 'results'}
        </p>
      )}
    </div>
  );
}
