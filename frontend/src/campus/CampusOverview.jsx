import { StatCard, StatRow } from '../shared/ui';
import { CAMPUS_STATS, CAMPUS, TODAY, ENTRIES } from '../mock/data';

/**
 * Screen 1 — campus overview.
 *
 * THERE IS NO "EXITS TODAY" STAT, and there never will be. Entry-only is a
 * product rule; a stat implying we know when people left would be inventing
 * data we never collected.
 */
export default function CampusOverview() {
  return (
    <div className="p-stack">
      <div>
        <h1 className="p-h1">{CAMPUS.name}</h1>
        <p className="p-caption">{TODAY}</p>
      </div>

      <StatRow>
        <StatCard value={CAMPUS_STATS.activePasses.toLocaleString()} label="Active passes" />
        <StatCard value={CAMPUS_STATS.entriesToday} label="Entries today" />
        <StatCard value={CAMPUS_STATS.visitorQueue} label="Visitor queue" hint="awaiting review" />
        <StatCard value={CAMPUS_STATS.gatesLive} label="Gates live" />
      </StatRow>

      <div className="p-card">
        <div className="p-pad-sm"><span className="p-label">Recent gate events</span></div>
        {ENTRIES.map((e) => (
          <div key={e.id} className="p-spread"
               style={{ padding: 'var(--s-3) var(--s-4)', borderTop: '1px solid var(--border)' }}>
            <span className="p-small">✓ {e.holder} · {e.gate}</span>
            <span className="p-mono p-muted">{e.day} {e.at}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
