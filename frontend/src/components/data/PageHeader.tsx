import { cn } from '@lib/utils/cn';

export interface PageHeaderProps {
  title: string;
  description?: string;
  actions?: React.ReactNode;
  className?: string;
}

export function PageHeader({ title, description, actions, className }: PageHeaderProps) {
  return (
    <header className={cn('flex flex-wrap items-start justify-between gap-[var(--sp-4)]', className)}>
      <div className="min-w-0">
        <h1 className="text-h1 text-[var(--ink-900)]">{title}</h1>
        {description && <p className="text-small mt-[var(--sp-1)] text-[var(--ink-500)]">{description}</p>}
      </div>
      {actions && <div className="flex items-center gap-[var(--sp-2)]">{actions}</div>}
    </header>
  );
}
