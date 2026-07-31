/**
 * Big number, label, optional delta. Neutral only - a stat is not a verdict.
 *
 * StatRow handles the 4-up / 2-up / 1-up responsive step so no screen has to
 * repeat the media queries.
 */
export function StatCard({ value, label, delta, hint }) {
  return (
    <div className="p-card p-stat">
      <span className="p-label">{label}</span>
      <span className="p-stat__value">{value}</span>
      {(delta || hint) && <span className="p-caption">{delta ?? hint}</span>}
    </div>
  );
}

export function StatRow({ children }) {
  return <div className="p-stat__row">{children}</div>;
}

export default StatCard;
