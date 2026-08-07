import type { LucideIcon } from 'lucide-react';
import { Inbox } from 'lucide-react';
import { cn } from '@lib/utils/cn';

export interface EmptyStateProps {
  icon?: LucideIcon;
  heading: string;
  /** One line. An empty screen is an invitation to act, not an apology. */
  description?: string;
  action?: React.ReactNode;
  className?: string;
}

export function EmptyState({ icon: Icon = Inbox, heading, description, action, className }: EmptyStateProps) {
  return (
    <div
      className={cn(
        'flex flex-col items-center justify-center gap-[var(--sp-3)] px-[var(--sp-6)] py-[var(--sp-12)] text-center',
        className,
      )}
    >
      <span className="flex size-14 items-center justify-center rounded-[var(--r-circle)] bg-[var(--brand-50)]">
        <Icon className="size-6 text-[var(--brand-300)]" aria-hidden />
      </span>
      <h3 className="text-h3 text-[var(--ink-900)]">{heading}</h3>
      {description && <p className="text-small max-w-sm text-[var(--ink-500)]">{description}</p>}
      {action && <div className="mt-[var(--sp-2)]">{action}</div>}
    </div>
  );
}
