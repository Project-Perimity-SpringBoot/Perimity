import { Fragment } from 'react';
import { PASS_STATUSES, type PassStatus } from '@/types/enums';
import { cn } from '@lib/utils/cn';

const ORDER: PassStatus[] = ['PENDING', 'ACTIVE', 'PAUSED', 'EXPIRED', 'REVOKED'];
const LABEL: Record<PassStatus, string> = {
  PENDING: 'Pending', ACTIVE: 'Active', PAUSED: 'Paused', EXPIRED: 'Expired', REVOKED: 'Revoked',
};

/**
 * PENDING → ACTIVE → PAUSED → EXPIRED / REVOKED, with the current state filled
 * and the rest hollow. The order mirrors the backend state machine; it is not a
 * decorative sequence.
 */
export function LifecycleStrip({ current, className }: { current: PassStatus; className?: string }) {
  const currentIndex = ORDER.indexOf(current);
  return (
    <ol
      className={cn('flex flex-wrap items-center gap-[var(--sp-1)]', className)}
      aria-label={`Pass lifecycle, currently ${LABEL[current]}`}
    >
      {ORDER.map((status, index) => {
        const isCurrent = status === current;
        const isPast = index < currentIndex;
        return (
          <Fragment key={status}>
            {index > 0 && <span aria-hidden className="h-px w-3 bg-[var(--border-strong)]" />}
            <li
              aria-current={isCurrent ? 'step' : undefined}
              className={cn(
                'rounded-[var(--r-pill)] border px-[var(--sp-2)] py-[2px] text-caption',
                isCurrent && 'border-[var(--ink-900)] bg-[var(--ink-900)] text-white',
                !isCurrent && isPast && 'border-[var(--border-strong)] bg-[var(--surface-sunken)] text-[var(--ink-500)]',
                !isCurrent && !isPast && 'border-[var(--border)] text-[var(--ink-400)]',
              )}
            >
              {LABEL[status]}
            </li>
          </Fragment>
        );
      })}
    </ol>
  );
}

export const LIFECYCLE_ORDER = ORDER;
export const LIFECYCLE_ALL = PASS_STATUSES;
