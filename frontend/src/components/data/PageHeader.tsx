import { Fragment } from 'react';
import { Link } from 'react-router';
import { ChevronRight } from 'lucide-react';
import { cn } from '@lib/utils/cn';

export interface Crumb {
  label: string;
  /** Omit on the last crumb — the current page is not a link to itself. */
  to?: string;
}

export interface PageHeaderProps {
  title: string;
  description?: string;
  actions?: React.ReactNode;
  /**
   * Rendered above the title. The current page should be the last entry and
   * carry no `to`, so it renders as text with aria-current rather than as a
   * link that goes nowhere.
   */
  breadcrumbs?: Crumb[];
  className?: string;
}

/**
 * The top of every screen in the product.
 *
 * Every page routes its title through here so that the title's size, the gap
 * to its description, and the position of the action buttons are decided in
 * one place. Pages that hand-rolled a header drifted immediately — the same
 * heading existed at 24px, 30px and 36px, and action buttons sat left on some
 * screens and right on others.
 */
export function PageHeader({
  title, description, actions, breadcrumbs, className,
}: PageHeaderProps) {
  return (
    <header className={cn('flex flex-col gap-[var(--sp-3)]', className)}>
      {breadcrumbs && breadcrumbs.length > 0 && (
        <nav aria-label="Breadcrumb">
          <ol className="text-small flex flex-wrap items-center gap-[var(--sp-1)] text-[var(--ink-500)]">
            {breadcrumbs.map((crumb, i) => {
              const last = i === breadcrumbs.length - 1;
              return (
                <Fragment key={`${crumb.label}-${i}`}>
                  {i > 0 && (
                    <ChevronRight className="size-3 shrink-0 text-[var(--ink-400)]" aria-hidden />
                  )}
                  <li>
                    {crumb.to && !last ? (
                      <Link
                        to={crumb.to}
                        className="rounded-[var(--r-sm)] transition-colors duration-[var(--motion-fast)] hover:text-[var(--ink-900)]"
                      >
                        {crumb.label}
                      </Link>
                    ) : (
                      <span aria-current={last ? 'page' : undefined} className="text-[var(--ink-700)]">
                        {crumb.label}
                      </span>
                    )}
                  </li>
                </Fragment>
              );
            })}
          </ol>
        </nav>
      )}

      <div className="flex flex-wrap items-start justify-between gap-[var(--sp-4)]">
        <div className="min-w-0">
          <h1 className="text-h1 text-[var(--ink-900)]">{title}</h1>
          {description && (
            <p className="text-body mt-[var(--sp-1)] max-w-2xl text-[var(--ink-500)]">
              {description}
            </p>
          )}
        </div>
        {actions && (
          <div className="flex flex-wrap items-center gap-[var(--sp-2)]">{actions}</div>
        )}
      </div>
    </header>
  );
}
