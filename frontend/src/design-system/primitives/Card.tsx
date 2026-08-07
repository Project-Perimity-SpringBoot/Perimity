import { cn } from '@lib/utils/cn';

export function Card({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <section className={cn('surface-panel', className)} {...props} />;
}

export function CardHeader({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <header
      className={cn(
        'flex items-start justify-between gap-[var(--sp-4)] border-b border-[var(--border)] px-[var(--sp-6)] py-[var(--sp-4)]',
        className,
      )}
      {...props}
    />
  );
}

export function CardTitle({ className, ...props }: React.HTMLAttributes<HTMLHeadingElement>) {
  return <h2 className={cn('text-h3 text-[var(--ink-900)]', className)} {...props} />;
}

export function CardDescription({ className, ...props }: React.HTMLAttributes<HTMLParagraphElement>) {
  return <p className={cn('text-small text-[var(--ink-500)]', className)} {...props} />;
}

export function CardBody({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('px-[var(--sp-6)] py-[var(--sp-4)]', className)} {...props} />;
}

export function CardFooter({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <footer
      className={cn(
        'flex items-center justify-end gap-[var(--sp-2)] border-t border-[var(--border)] px-[var(--sp-6)] py-[var(--sp-3)]',
        className,
      )}
      {...props}
    />
  );
}
