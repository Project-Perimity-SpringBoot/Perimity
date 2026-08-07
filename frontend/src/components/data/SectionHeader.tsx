import type { LucideIcon } from 'lucide-react';
import { cn } from '@lib/utils/cn';

export interface SectionHeaderProps {
  title: string;
  description?: string;
  icon?: LucideIcon;
  /** A count or state word rendered beside the title. */
  badge?: React.ReactNode;
  /** Right-hand link or button. */
  actions?: React.ReactNode;
  /** Underlines the header — for a section that opens a page-level band. */
  divided?: boolean;
  id?: string;
  className?: string;
}

/**
 * A heading for a band WITHIN a page, one level below PageHeader.
 *
 * PageHeader answers "what screen am I on". This answers "what is this group
 * of things", and the size difference between them is the whole point: they
 * were being written by hand as text-lg font-bold in one file and text-base
 * font-bold in the next, which flattened the hierarchy so that a section
 * competed with the page title above it.
 *
 * The icon is decorative and sized once here. It was drifting between size-4,
 * size-5 and size-6 across the pages that drew this pattern by hand, which is
 * the kind of difference nobody can name but everybody sees.
 */
export function SectionHeader({
  title, description, icon: Icon, badge, actions, divided, id, className,
}: SectionHeaderProps) {
  return (
    <div
      className={cn(
        'flex flex-wrap items-start justify-between gap-[var(--sp-3)]',
        divided && 'border-b border-[var(--border)] pb-[var(--sp-3)]',
        className,
      )}
    >
      <div className="flex min-w-0 items-start gap-[var(--sp-3)]">
        {Icon && (
          <span className="flex size-9 shrink-0 items-center justify-center rounded-[var(--r-sm)] bg-[var(--brand-50)]">
            <Icon className="size-4 text-[var(--brand-600)]" aria-hidden />
          </span>
        )}
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-[var(--sp-2)]">
            <h2 id={id} className="text-h3 text-[var(--ink-900)]">
              {title}
            </h2>
            {badge}
          </div>
          {description && (
            <p className="text-small mt-[var(--sp-1)] text-[var(--ink-500)]">{description}</p>
          )}
        </div>
      </div>
      {actions && <div className="flex items-center gap-[var(--sp-2)]">{actions}</div>}
    </div>
  );
}
