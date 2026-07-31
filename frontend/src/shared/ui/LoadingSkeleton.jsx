/**
 * Skeletons rather than spinners for anything with a known shape.
 *
 * A spinner says "wait"; a skeleton says "wait, and here is what is arriving".
 * On a table that difference is the reason the page does not appear to jump
 * when the data lands.
 */
export function Skeleton({ w = '100%', h = 16, r = 'var(--r-sm)', style }) {
  return <div className="p-skel" style={{ width: w, height: h, borderRadius: r, ...style }} />;
}

export function TableSkeleton({ rows = 6, cols = 4 }) {
  return (
    <div className="p-stack" style={{ padding: 'var(--s-4)' }}>
      <Skeleton h={12} w="30%" />
      {Array.from({ length: rows }).map((_, r) => (
        <div className="p-row" key={r}>
          {Array.from({ length: cols }).map((_, c) => (
            <Skeleton key={c} h={14} w={c === 0 ? '28%' : '18%'} />
          ))}
        </div>
      ))}
    </div>
  );
}

export function CardGridSkeleton({ count = 4 }) {
  return (
    <div className="p-stat__row">
      {Array.from({ length: count }).map((_, i) => (
        <div className="p-card p-stat" key={i}>
          <Skeleton h={10} w="40%" /><Skeleton h={28} w="60%" />
        </div>
      ))}
    </div>
  );
}

export function DetailSkeleton() {
  return (
    <div className="p-card p-pad p-stack">
      <Skeleton h={20} w="45%" />
      <Skeleton h={14} w="70%" />
      <Skeleton h={180} r="var(--r-md)" />
      <Skeleton h={14} w="55%" />
    </div>
  );
}

export default Skeleton;
