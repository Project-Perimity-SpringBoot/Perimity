import { useMemo, useState } from 'react';
import EmptyState from './EmptyState';
import { TableSkeleton } from './LoadingSkeleton';

/**
 * The table used by every list screen in the product, and its mobile form.
 *
 * BELOW 640px THIS STOPS BEING A TABLE. Each row becomes a card: the column
 * marked `primary` is the heading, the rest become label/value pairs. Both are
 * rendered and CSS picks one - which costs a little DOM and removes an entire
 * class of "the last column is off-screen on a phone and nobody noticed" bugs.
 *
 * Sorting is client-side and deliberately simple. Once a list is long enough
 * to need server-side sorting it also needs server-side paging, and that is a
 * different component with a different contract.
 *
 * columns: [{ key, header, primary?, sortable?, render?(row), mobileHide? }]
 */
export default function DataTable({
  columns, rows, loading, empty, rowKey = (r, i) => r.id ?? i,
  onRowClick, page, pageSize = 20, onPageChange, total,
}) {
  const [sort, setSort] = useState(null);   // { key, dir }

  const sorted = useMemo(() => {
    if (!sort) return rows;
    const col = columns.find((c) => c.key === sort.key);
    if (!col) return rows;
    return [...rows].sort((a, b) => {
      const av = a[sort.key], bv = b[sort.key];
      if (av == null) return 1;
      if (bv == null) return -1;
      const r = typeof av === 'number' ? av - bv : String(av).localeCompare(String(bv));
      return sort.dir === 'asc' ? r : -r;
    });
  }, [rows, sort, columns]);

  const toggle = (key) =>
    setSort((s) => (s?.key === key
      ? { key, dir: s.dir === 'asc' ? 'desc' : 'asc' }
      : { key, dir: 'asc' }));

  if (loading) return <TableSkeleton rows={6} cols={columns.length} />;
  if (!rows?.length) return empty ?? <EmptyState title="Nothing here yet" />;

  const primary = columns.find((c) => c.primary) ?? columns[0];
  const cell = (col, row) => (col.render ? col.render(row) : row[col.key]);

  return (
    <div>
      {/* desktop + tablet */}
      <div className="p-table-wrap">
        <table className="p-table">
          <thead>
            <tr>
              {columns.map((c) => (
                <th
                  key={c.key}
                  data-sortable={c.sortable || undefined}
                  onClick={c.sortable ? () => toggle(c.key) : undefined}
                  aria-sort={sort?.key === c.key
                    ? (sort.dir === 'asc' ? 'ascending' : 'descending')
                    : undefined}
                >
                  {c.header}
                  {c.sortable && (
                    <span className="p-table__sort" aria-hidden>
                      {sort?.key === c.key ? (sort.dir === 'asc' ? '↑' : '↓') : '↕'}
                    </span>
                  )}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {sorted.map((row, i) => (
              <tr
                key={rowKey(row, i)}
                onClick={onRowClick ? () => onRowClick(row) : undefined}
                style={onRowClick ? { cursor: 'pointer' } : undefined}
              >
                {columns.map((c) => <td key={c.key}>{cell(c, row)}</td>)}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* mobile */}
      <div className="p-cards">
        {sorted.map((row, i) => (
          <div
            key={rowKey(row, i)}
            className="p-card p-cards__item"
            onClick={onRowClick ? () => onRowClick(row) : undefined}
          >
            <div className="p-cards__title">{cell(primary, row)}</div>
            {columns
              .filter((c) => c !== primary && !c.mobileHide)
              .map((c) => (
                <div className="p-cards__kv" key={c.key}>
                  <span className="p-cards__k">{c.header}</span>
                  <span>{cell(c, row)}</span>
                </div>
              ))}
          </div>
        ))}
      </div>

      {onPageChange && (
        <div className="p-pager">
          <span className="p-caption">
            {total != null
              ? `${(page - 1) * pageSize + 1}–${Math.min(page * pageSize, total)} of ${total}`
              : `Page ${page}`}
          </span>
          <div className="p-row">
            <button className="p-btn p-btn--secondary" disabled={page <= 1}
                    onClick={() => onPageChange(page - 1)}>Previous</button>
            <button className="p-btn p-btn--secondary"
                    disabled={total != null && page * pageSize >= total}
                    onClick={() => onPageChange(page + 1)}>Next</button>
          </div>
        </div>
      )}
    </div>
  );
}
