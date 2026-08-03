import { cn } from '@lib/utils/cn';

export function Skeleton({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      aria-hidden
      className={cn(
        'animate-pulse rounded-[var(--r-sm)] bg-[var(--surface-sunken)]',
        className,
      )}
      {...props}
    />
  );
}

export function SkeletonText({ lines = 3, className }: { lines?: number; className?: string }) {
  return (
    <div className={cn('flex flex-col gap-[var(--sp-2)]', className)}>
      {Array.from({ length: lines }, (_, i) => (
        <Skeleton key={i} className={cn('h-3', i === lines - 1 ? 'w-2/3' : 'w-full')} />
      ))}
    </div>
  );
}

export function SkeletonTable({ rows = 6, columns = 5 }: { rows?: number; columns?: number }) {
  return (
    <div className="flex flex-col gap-[var(--sp-2)] p-[var(--sp-4)]" role="status" aria-label="Loading table">
      {Array.from({ length: rows }, (_, r) => (
        <div key={r} className="flex gap-[var(--sp-4)]">
          {Array.from({ length: columns }, (_, c) => (
            <Skeleton key={c} className={cn('h-4 flex-1', c === 0 && 'max-w-48')} />
          ))}
        </div>
      ))}
    </div>
  );
}

export function SkeletonCards({ count = 4 }: { count?: number }) {
  return (
    <div className="grid gap-[var(--sp-4)] sm:grid-cols-2 lg:grid-cols-4" role="status" aria-label="Loading">
      {Array.from({ length: count }, (_, i) => (
        <Skeleton key={i} className="h-24" />
      ))}
    </div>
  );
}
