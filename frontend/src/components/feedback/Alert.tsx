import type { LucideIcon } from 'lucide-react';
import { AlertTriangle, CheckCircle2, Info, TriangleAlert } from 'lucide-react';
import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '@lib/utils/cn';

/**
 * The inline message banner: "that worked", "careful", "that failed".
 *
 * ==========================================================================
 * WHY THIS EXISTS
 * ==========================================================================
 * It did not, and every screen that needed one built its own. Counting only
 * the faculty feature there were fifteen, no two alike — some rounded-xl and
 * some rounded-2xl, padding between 12px and 20px, four different greens,
 * icons at size-4 and size-5, and half of them with no icon at all. A user
 * moving between two pages saw two different products saying the same thing.
 *
 * Tones map to the --notice-* aliases rather than to raw colours, so retuning
 * every alert in the application is one edit in tokens.css.
 *
 * ==========================================================================
 * ROLE, AND WHY IT IS NOT ALWAYS "alert"
 * ==========================================================================
 * role="alert" is an assertive live region: a screen reader interrupts
 * whatever it is currently saying to read it. That is correct for a failure
 * the user must know about now, and actively hostile for a standing note that
 * is simply part of the page — it fires on every render and talks over the
 * reader as they navigate. So the assertive role is applied to danger and
 * warning only, and anything decorative passes `live={false}`.
 */
const alert = cva(
  'flex items-start gap-[var(--sp-3)] rounded-[var(--r-md)] border p-[var(--sp-4)]',
  {
    variants: {
      tone: {
        info: 'border-[var(--notice-info-border)] bg-[var(--notice-info-bg)] text-[var(--notice-info-fg)]',
        success: 'border-[var(--notice-success-border)] bg-[var(--notice-success-bg)] text-[var(--notice-success-fg)]',
        warning: 'border-[var(--notice-warning-border)] bg-[var(--notice-warning-bg)] text-[var(--notice-warning-fg)]',
        danger: 'border-[var(--notice-danger-border)] bg-[var(--notice-danger-bg)] text-[var(--notice-danger-fg)]',
      },
    },
    defaultVariants: { tone: 'info' },
  },
);

const DEFAULT_ICON: Record<NonNullable<AlertProps['tone']>, LucideIcon> = {
  info: Info,
  success: CheckCircle2,
  warning: AlertTriangle,
  danger: TriangleAlert,
};

export interface AlertProps
  extends Omit<React.HTMLAttributes<HTMLDivElement>, 'title'>,
    VariantProps<typeof alert> {
  /** Bolded first line. Omit it for a single-sentence note. */
  title?: React.ReactNode;
  icon?: LucideIcon;
  /** Set false for a standing note that must not interrupt a screen reader. */
  live?: boolean;
  children?: React.ReactNode;
}

export function Alert({
  tone = 'info', title, icon, live, className, children, ...props
}: AlertProps) {
  const Icon = icon ?? DEFAULT_ICON[tone ?? 'info'];
  const assertive = live ?? (tone === 'danger' || tone === 'warning');

  return (
    <div
      className={cn(alert({ tone }), className)}
      {...(assertive ? { role: 'alert' } : {})}
      {...props}
    >
      <Icon className="mt-[2px] size-4 shrink-0" aria-hidden />
      <div className="min-w-0 flex-1">
        {title && <p className="text-body-md">{title}</p>}
        {children && (
          <div className={cn('text-small', title && 'mt-[var(--sp-1)]')}>{children}</div>
        )}
      </div>
    </div>
  );
}
