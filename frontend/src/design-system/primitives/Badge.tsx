import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '@lib/utils/cn';

/**
 * Neutral by default and on purpose. Pass statuses are differentiated by their
 * WORD, never by colour — a red REVOKED badge would be indistinguishable at a
 * glance from a guard's DENY verdict, which is the one place red means stop.
 */
const badge = cva(
  'inline-flex items-center gap-[var(--sp-1)] rounded-[var(--r-pill)] border px-[var(--sp-2)] py-[2px] ' +
    'text-caption whitespace-nowrap',
  {
    variants: {
      tone: {
        neutral: 'bg-[var(--status-bg)] text-[var(--status-fg)] border-[var(--status-border)]',
        brand: 'bg-[var(--brand-50)] text-[var(--brand-600)] border-[var(--brand-200)]',
        daily: 'bg-transparent text-[var(--pass-daily)] border-[var(--pass-daily)]',
        event: 'bg-transparent text-[var(--pass-event)] border-[var(--pass-event)]',
        visitor: 'bg-transparent text-[var(--pass-visitor)] border-[var(--pass-visitor)]',
      },
    },
    defaultVariants: { tone: 'neutral' },
  },
);

export interface BadgeProps
  extends React.HTMLAttributes<HTMLSpanElement>,
    VariantProps<typeof badge> {}

export function Badge({ className, tone, ...props }: BadgeProps) {
  return <span className={cn(badge({ tone }), className)} {...props} />;
}
