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

export function StatCard({
  label, value, icon: Icon, hint, loading, unavailableReason, className,
}: StatCardProps) {
  return (
    <div className={cn('surface-card p-[var(--sp-4)]', className)}>
      <div className="flex items-start justify-between gap-[var(--sp-2)]">
        <p className="text-label text-[var(--ink-500)]">{label}</p>
        {Icon && <Icon className="size-4 shrink-0 text-[var(--ink-400)]" aria-hidden />}
      </div>

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
  );
}
