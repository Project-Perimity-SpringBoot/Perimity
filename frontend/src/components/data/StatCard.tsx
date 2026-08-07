import type { LucideIcon } from 'lucide-react';
import { Skeleton } from '@ui/index';
import { cn } from '@lib/utils/cn';

export interface StatCardProps {
  label: string;
  value: number | string | null;
  icon?: LucideIcon;
  hint?: string;
  loading?: boolean;
  /**
   * Some platform-level metrics are unreachable for a Super Admin because every
   * count endpoint is campus-scoped. Say so plainly instead of spinning forever.
   */
  unavailableReason?: string;
  className?: string;
}

/**
 * One metric. The tile in a dashboard's top row.
 *
 * The icon sits in a filled brand tile on the right, opposite the label —
 * three feature screens had already drawn this by hand, each with its own
 * accent colour, which meant a green tile on one dashboard and a rose tile on
 * the next carried no meaning while looking exactly like they did. The tile is
 * brand-tinted here and nothing else, so the number is what the eye lands on.
 *
 * Notably NOT tinted per-metric: a red tile on "refused entries" reads as an
 * alarm on a number that is simply a count, and it borrows the guard's deny
 * colour to do it.
 */
export function StatCard({
  label, value, icon: Icon, hint, loading, unavailableReason, className,
}: StatCardProps) {
  return (
    <div className={cn('surface-card flex items-start justify-between gap-[var(--sp-3)] p-[var(--sp-4)]', className)}>
      <div className="min-w-0 flex-1">
        <p className="text-label text-[var(--ink-500)]">{label}</p>

        {unavailableReason ? (
          <p className="text-small mt-[var(--sp-2)] text-[var(--ink-400)]">{unavailableReason}</p>
        ) : loading ? (
          <Skeleton className="mt-[var(--sp-2)] h-8 w-20" />
        ) : (
          <p className="text-display mt-[var(--sp-1)] text-[var(--ink-900)] tabular-nums">
            {value ?? '—'}
          </p>
        )}

        {hint && !unavailableReason && (
          <p className="text-caption mt-[var(--sp-1)] text-[var(--ink-500)]">{hint}</p>
        )}
      </div>

      {Icon && (
        <span className="flex size-10 shrink-0 items-center justify-center rounded-[var(--r-md)] bg-[var(--brand-50)]">
          <Icon className="size-5 text-[var(--brand-600)]" aria-hidden />
        </span>
      )}
    </div>
  );
}
