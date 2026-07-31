import { Link } from 'react-router-dom';
import { PassCard, StatCard, StatRow } from '../shared/ui';
import { PASSES, PEOPLE, CAMPUS, ENTRIES, EVENT } from '../mock/data';

/**
 * Screen 1 — student home.
 *
 * TWO PASSES SIDE BY SIDE is the point of this screen. A pass is not a person:
 * one identity holds a standing DAILY pass and a time-boxed EVENT pass at the
 * same time, and both being active is normal. Showing them as siblings is what
 * makes that legible without explaining it.
 *
 * Recent activity is ENTRY EVENTS ONLY. No exit rows, no durations, ever.
 */
export default function StudentDashboard() {
  const daily = PASSES.find((p) => p.type === 'daily' && p.status === 'ACTIVE');
  const event = PASSES.find((p) => p.type === 'event' && p.status === 'PENDING');

  return (
    <div className="p-stack">
      <div>
        <h1 className="p-h1">Good morning, {PEOPLE.student.name}</h1>
        <p className="p-caption">{PEOPLE.student.dept} · {CAMPUS.name}</p>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(280px,1fr))', gap: 'var(--s-4)' }}>
        <PassCard type="daily" holder={daily.holder} code={daily.code} campus={CAMPUS.name}
                  validity={daily.validity} status={daily.status} variant="compact"
                  footer={<Link to="/student/pass" className="p-small">View pass →</Link>} />
        <PassCard type="event" holder={event.holder} code={event.code} eventName={EVENT.name}
                  campus={CAMPUS.name} validity={event.validity} status={event.status}
                  note={event.note} variant="compact" />
      </div>

      <StatRow>
        <StatCard value="3" label="Entries today" />
        <StatCard value="Verified" label="Profile status" />
        <StatCard value="2" label="Upcoming events" />
      </StatRow>

      <div className="p-card">
        <div className="p-pad-sm"><span className="p-label">Recent activity</span></div>
        {ENTRIES.map((e) => (
          <div key={e.id} className="p-spread" style={{ padding: 'var(--s-3) var(--s-4)', borderTop: '1px solid var(--border)' }}>
            <span className="p-small">Entry · {e.gate}{e.attributedTo && ` · ${e.attributedTo}`}</span>
            <span className="p-mono p-muted">{e.day} {e.at}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
