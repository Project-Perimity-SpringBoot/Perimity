/**
 * Search + up to three dropdowns + a result count.
 *
 * The count is not decoration: on a filtered list it is the only thing that
 * tells you whether "no rows" means the filter is wrong or the data is empty.
 *
 * filters: [{ key, label, value, options, onChange }]
 */
export default function SearchFilterBar({
  search, onSearch, placeholder = 'Search…', filters = [], count, countLabel = 'results', actions,
}) {
  return (
    <div className="p-filters">
      <div className="p-filters__search">
        <input
          className="p-input" type="search" value={search}
          placeholder={placeholder} onChange={(e) => onSearch?.(e.target.value)}
          aria-label={placeholder}
        />
      </div>

      {filters.map((f) => (
        <select
          key={f.key} className="p-select" value={f.value}
          onChange={(e) => f.onChange(e.target.value)} aria-label={f.label}
          style={{ width: 'auto', minWidth: 140 }}
        >
          <option value="">{f.label}: All</option>
          {f.options.map((o) => (
            <option key={o.value ?? o} value={o.value ?? o}>{o.label ?? o}</option>
          ))}
        </select>
      ))}

      {count != null && <span className="p-caption">{count} {countLabel}</span>}
      {actions && <div className="p-row" style={{ marginLeft: 'auto' }}>{actions}</div>}
    </div>
  );
}
