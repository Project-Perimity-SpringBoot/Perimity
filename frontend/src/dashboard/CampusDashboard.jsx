import { Link } from 'react-router-dom';
import { StatCard, StatRow, DataTable, EmptyState, ErrorState,
         CardGridSkeleton, Button, StatusBadge } from '../shared/ui';
import { guard, gatepass, campus, useApi } from '../api';
import { useAuth } from '../shared/AuthContext';

/**
 * The Campus Admin's morning screen. LIVE.
 *
 * Today's entries, today's denials, active passes, and what is waiting on a
 * human. NO "exits today" — the product does not scan people out, and a stat
 * card is exactly where that misunderstanding would get baked in.
 *
 * Denials are shown next to entries rather than buried, because a denial spike
 * is the earliest signal that something is misconfigured — a gate deactivated
 * by accident, a campus config change, an event that ended a day early.
 */
const startOfToday = () => { const d = new Date(); d.setHours(0, 0, 0, 0); return d.toISOString().slice(0, 19); };

export default function CampusDashboard() {
  const { user } = useAuth();
  const filter = { campusId: user.campusId, from: startOfToday(), to: new Date().toISOString().slice(0, 19) };

  const { data: stats, loading, error, reload } =
    useApi(() => guard.entryStats(filter), [user.campusId]);
  const { data: passCount } = useApi(() => gatepass.passCount('ACTIVE'), []);
  const { data: pending }   = useApi(() => gatepass.pendingCount(), []);
  const { data: running }   = useApi(() => gatepass.runningEvents(), []);
  const { data: gates }     = useApi(() => campus.gates(user.campusId), [user.campusId]);

  if (loading) return <CardGridSkeleton count={4} />;
  if (error)   return <ErrorState title="Could not load today's figures"
                                  message={error.message} onRetry={reload} />;

  const activeGates = (gates ?? []).filter((g) => g.active);
  const events = running?.content ?? running ?? [];

  return (
    <div className="p-stack">
      <div>
        <h1 className="p-h1">Today</h1>
        <p className="p-caption">{new Date().toLocaleDateString(undefined,
          { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}</p>
      </div>

      <StatRow>
        <StatCard value={stats?.entriesPermitted ?? 0} label="Entries today" />
        <StatCard value={stats?.deniedCount ?? 0} label="Denied today"
                  hint="A spike usually means a configuration change, not a security event." />
        <StatCard value={passCount ?? 0} label="Active passes" />
        <StatCard value={activeGates.length} label="Gates live" />
      </StatRow>

      {(pending ?? 0) > 0 && (
        <div className="p-card p-pad-sm">
          <div className="p-spread">
            <div>
              <span className="p-label">Waiting on you</span>
              <p className="p-caption" style={{ marginBottom: 0 }}>
                {pending} visitor {pending === 1 ? 'request' : 'requests'} pending approval.
              </p>
            </div>
            <Link to="/approvals"><Button>Review</Button></Link>
          </div>
        </div>
      )}

      <div className="p-card">
        <div className="p-pad-sm"><span className="p-label">Running today</span></div>
        <DataTable
          columns={[
            { key: 'name', header: 'Event', primary: true,
              render: (e) => <Link to={`/events/${e.id}`}>{e.name}</Link> },
            { key: 'validTo', header: 'Runs until' },
            { key: 'issuedPassCount', header: 'Passes issued' },
            { key: 's', header: '', render: () => <StatusBadge status="ACTIVE" /> },
          ]}
          rows={events}
          empty={<EmptyState icon="⊞" title="No events running today"
                             message="Daily passes are unaffected — this is only about event passes." />}
        />
      </div>
    </div>
  );
}
