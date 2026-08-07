import {
  flexRender, getCoreRowModel, getSortedRowModel, useReactTable,
  type ColumnDef, type SortingState,
} from '@tanstack/react-table';
import { useState } from 'react';
import { ArrowDown, ArrowUp, ChevronsUpDown } from 'lucide-react';
import { SkeletonTable } from '@ui/index';
import { cn } from '@lib/utils/cn';
import type { Paged } from '@/types/api';
import { EmptyState } from '../feedback/EmptyState';
import { Pagination } from './Pagination';

export interface DataTableProps<T> {
  columns: ColumnDef<T, unknown>[];
  data: T[];
  loading?: boolean;
  /** Present it and pagination renders; omit it for a bare list. */
  page?: Paged<T>;
  onPageChange?: (page: number) => void;
  emptyHeading?: string;
  emptyDescription?: string;
  onRowClick?: (row: T) => void;
  /** Used as the card heading in the sub-640px stacked form. */
  mobilePrimaryColumn?: string;
  getRowId?: (row: T) => string;
}

/**
 * The stacked-card form needs a plain string label, and a header may be a
 * string or a render function. Only the string form is usable as a <dt>.
 */
function headerLabel(header: unknown): string {
  return typeof header === 'string' ? header : '';
}

export function DataTable<T>({
  columns, data, loading, page, onPageChange,
  emptyHeading = 'Nothing here yet', emptyDescription,
  onRowClick, mobilePrimaryColumn, getRowId,
}: DataTableProps<T>) {
  const [sorting, setSorting] = useState<SortingState>([]);

  const table = useReactTable({
    data,
    columns,
    state: { sorting },
    onSortingChange: setSorting,
    getCoreRowModel: getCoreRowModel(),
    // Client-side only: no endpoint in this backend accepts a text search, and
    // several forbid a Sort param outright, so ordering stays on the client.
    getSortedRowModel: getSortedRowModel(),
    ...(getRowId ? { getRowId: (row: T) => getRowId(row) } : {}),
  });

  if (loading) return <SkeletonTable rows={6} columns={columns.length} />;
  if (data.length === 0) {
    return <EmptyState heading={emptyHeading} {...(emptyDescription ? { description: emptyDescription } : {})} />;
  }

  return (
    <div className="flex flex-col">
      {/* Desktop and tablet: a real table. */}
      <div className="hidden overflow-x-auto scrollbar-thin sm:block">
        <table className="w-full border-collapse text-left">
          <thead className="bg-[var(--surface-subtle)]">
            {table.getHeaderGroups().map((group) => (
              <tr key={group.id} className="border-b border-[var(--border)]">
                {group.headers.map((header) => {
                  const sortable = header.column.getCanSort();
                  const dir = header.column.getIsSorted();
                  return (
                    <th
                      key={header.id}
                      scope="col"
                      className="text-label whitespace-nowrap px-[var(--sp-4)] py-[var(--sp-3)] text-[var(--ink-500)]"
                    >
                      {header.isPlaceholder ? null : sortable ? (
                        <button
                          type="button"
                          onClick={header.column.getToggleSortingHandler()}
                          className="inline-flex items-center gap-[var(--sp-1)] hover:text-[var(--ink-900)]"
                        >
                          {flexRender(header.column.columnDef.header, header.getContext())}
                          {dir === 'asc' ? (
                            <ArrowUp className="size-3" aria-hidden />
                          ) : dir === 'desc' ? (
                            <ArrowDown className="size-3" aria-hidden />
                          ) : (
                            <ChevronsUpDown className="size-3 opacity-50" aria-hidden />
                          )}
                        </button>
                      ) : (
                        flexRender(header.column.columnDef.header, header.getContext())
                      )}
                    </th>
                  );
                })}
              </tr>
            ))}
          </thead>
          <tbody>
            {/*
              * Zebra striping is gone, and that is deliberate rather than a
              * simplification. It was fighting the hover tint: every other row
              * already had a fill, so hovering an odd row was a clear change
              * and hovering an even row was almost none — the affordance was
              * inconsistent depending on where in the table you happened to
              * be. A hairline per row separates them, and the fill is reserved
              * for the row under the cursor, where it means something.
              */}
            {table.getRowModel().rows.map((row) => (
              <tr
                key={row.id}
                onClick={onRowClick ? () => onRowClick(row.original) : undefined}
                className={cn(
                  'border-b border-[var(--border)] last:border-0',
                  'transition-colors duration-[var(--motion-fast)]',
                  onRowClick
                    ? 'cursor-pointer hover:bg-[var(--brand-50)]'
                    : 'hover:bg-[var(--surface-subtle)]',
                )}
              >
                {row.getVisibleCells().map((cell) => (
                  <td key={cell.id} className="text-body px-[var(--sp-4)] py-[var(--sp-4)] align-middle">
                    {flexRender(cell.column.columnDef.cell, cell.getContext())}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Below 640px the same rows become stacked cards: the primary field is
          the heading, everything else is a label/value pair. */}
      <ul className="flex flex-col gap-[var(--sp-2)] sm:hidden">
        {table.getRowModel().rows.map((row) => {
          const cells = row.getVisibleCells();
          const primary = mobilePrimaryColumn
            ? cells.find((c) => c.column.id === mobilePrimaryColumn)
            : cells[0];
          const rest = cells.filter((c) => c.id !== primary?.id);
          return (
            <li
              key={row.id}
              onClick={onRowClick ? () => onRowClick(row.original) : undefined}
              className={cn('surface-card p-[var(--sp-4)]', onRowClick && 'lift cursor-pointer')}
            >
              {/*
                * A DIV, not a P.
                *
                * This wraps whatever a column's cell renderer returns, and a
                * cell is free to return block content - UsersPage's name column
                * renders a <div> holding a <p> for the name and another for the
                * email. Nesting that inside a <p> is invalid HTML: the browser
                * auto-closes the outer paragraph, so React's tree and the real
                * DOM stop matching and every affected row logged a hydration
                * error. Nothing renders a paragraph here that needs to be one.
                */}
              {primary && (
                <div className="text-body-md text-[var(--ink-900)]">
                  {flexRender(primary.column.columnDef.cell, primary.getContext())}
                </div>
              )}
              <dl className="mt-[var(--sp-2)] grid grid-cols-[auto_1fr] gap-x-[var(--sp-4)] gap-y-[var(--sp-1)]">
                {rest.map((cell) => (
                  <div key={cell.id} className="contents">
                    <dt className="text-caption text-[var(--ink-500)]">
                      {headerLabel(cell.column.columnDef.header)}
                    </dt>
                    <dd className="text-small text-[var(--ink-900)]">
                      {flexRender(cell.column.columnDef.cell, cell.getContext())}
                    </dd>
                  </div>
                ))}
              </dl>
            </li>
          );
        })}
      </ul>

      {page && onPageChange && <Pagination page={page} onPageChange={onPageChange} />}
    </div>
  );
}
