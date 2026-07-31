import { EmptyState } from '../shared/ui';
import { ENTRIES } from '../mock/data';

/**
 * Screen 5 — gate entries, grouped by day.
 *
 * ENTRY ONLY. No exit column, no duration, no "time on campus". The paper
 * register this replaces only ever recorded arrival, and inventing an exit we
 * never observed would be inventing data.
 *
 * Where an entry was attributed to an event, say so — that is Behavior 2 made
 * visible, and it is the line that explains why a daily-QR scan appears in an
 * event's attendance list.
 */
export default function EntryHistory() {
  const days = ENTRIES.reduce((acc, e) => {
    (acc[e.day] ||= []).push(e);
    return acc;
  }, {});

  if (!ENTRIES.length) {
    return <EmptyState icon="○" title="No entries yet"
                       message="Your gate entries will appear here after your first scan." />;
  }

  return (
    <div className="p-stack">
      <div>
        <h1 className="p-h1">Entry history</h1>
        <p className="p-caption">Every entry is a separate row — you may enter more than once a day.</p>
      </div>

      {Object.entries(days).map(([day, list]) => (
        <div className="p-card" key={day}>
          <div className="p-pad-sm"><span className="p-label">{day}</span></div>
          {list.map((e) => (
            <div key={e.id} className="p-spread"
                 style={{ padding: 'var(--s-3) var(--s-4)', borderTop: '1px solid var(--border)' }}>
              <div>
                <div className="p-small">Entry · {e.gate}</div>
                {e.attributedTo && (
                  <div className="p-caption">attributed to {e.attributedTo}</div>
                )}
              </div>
              <span className="p-mono p-muted">{e.at}</span>
            </div>
          ))}
        </div>
      ))}
    </div>
  );
}
