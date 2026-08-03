import { cn } from '@lib/utils/cn';

export interface DescriptionItem {
  label: string;
  /** Rendered as a text node. Server-authored strings are never HTML. */
  value: React.ReactNode;
  /** Spans both columns — long free text such as a purpose or a reason. */
  wide?: boolean;
}

/**
 * The key/value panel behind every detail drawer in the product.
 *
 * A real <dl>, because a screen reader announcing "Host, Dr A Rao" is the whole
 * point of a definition list and a two-column grid of <div>s announces nothing.
 * A null value renders an em dash rather than collapsing the row: a missing
 * field the reader can see is absent beats a row that silently is not there.
 */
export function DescriptionList({
  items,
  columns = 2,
  className,
}: {
  items: DescriptionItem[];
  columns?: 1 | 2;
  className?: string;
}) {
  return (
    <dl
      className={cn(
        'grid gap-x-[var(--sp-6)] gap-y-[var(--sp-4)]',
        columns === 2 ? 'sm:grid-cols-2' : 'grid-cols-1',
        className,
      )}
    >
      {items.map((item) => (
        <div key={item.label} className={cn('min-w-0', item.wide && 'sm:col-span-2')}>
          <dt className="text-label text-[var(--ink-500)]">{item.label}</dt>
          <dd className="text-body mt-[var(--sp-1)] break-words text-[var(--ink-900)]">
            {item.value === null || item.value === undefined || item.value === '' ? (
              <span className="text-[var(--ink-400)]">—</span>
            ) : (
              item.value
            )}
          </dd>
        </div>
      ))}
    </dl>
  );
}
