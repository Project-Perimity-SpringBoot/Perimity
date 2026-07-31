/**
 * Determinate with a count, or indeterminate.
 *
 * The count matters more than the bar on the bulk screen: "312 of 580" tells a
 * faculty member whether to wait or walk away. A percentage alone does not,
 * because it does not say how big the job is.
 */
export default function ProgressBar({ value, max, label, indeterminate }) {
  const pct = indeterminate ? 0 : Math.min(100, Math.round(((value ?? 0) / (max || 1)) * 100));

  return (
    <div className="p-stack" style={{ gap: 'var(--s-2)' }}>
      {(label || !indeterminate) && (
        <div className="p-spread">
          {label && <span className="p-small">{label}</span>}
          {!indeterminate && (
            <span className="p-mono">{value} of {max}</span>
          )}
        </div>
      )}
      <div
        className={`p-progress ${indeterminate ? 'p-progress--indeterminate' : ''}`}
        role="progressbar"
        aria-valuenow={indeterminate ? undefined : value}
        aria-valuemin={0} aria-valuemax={max} aria-label={label}
      >
        <div className="p-progress__fill" style={indeterminate ? undefined : { width: `${pct}%` }} />
      </div>
    </div>
  );
}
