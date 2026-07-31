import { StatCard, StatRow, DataTable, ErrorState, CardGridSkeleton, StatusBadge } from '../shared/ui';
import { campus, useApi } from '../api';

/**
 * The Super Admin's cross-campus view. LIVE against campus-service.
 *
 * Renders with the platform tone — darker bar, "Platform" chip — because this
 * console and the Campus Admin one sit side by side in someone's tabs and
 * suspending the wrong campus is unrecoverable.
 *
 * The suspended row is not hidden or greyed into invisibility. A suspended
 * campus is exactly the one somebody needs to find.
 */
export default function PlatformDashboard() {
  const { data, loading, error, reload } = useApi(() => campus.stats(), []);
  const { data: list } = useApi(() => campus.list({ size: 100 }), []);

  if (loading) return <CardGridSkeleton count={4} />;
  if (error)   return <ErrorState title="Could not load platform statistics"
                                  message={error.message} onRetry={reload} />;

  const rows = list?.content ?? list ?? [];

  return (
    <div className="p-stack">
      <div>
        <h1 className="p-h1">Platform</h1>
        <p className="p-caption">All campuses</p>
      </div>

      {/*
        * CampusStatsResponse carries exactly three numbers — total, active and
        * inactive campuses. There is no platform-wide gate or user count on the
        * backend, so the gate figure is summed from the campus list rather than
        * invented, and there is no "Users" card at all. A card reading "—" for
        * the life of the product is worse than one that is not there.
        */}
      <StatRow>
        <StatCard value={data?.totalCampuses ?? rows.length} label="Campuses" />
        <StatCard value={data?.activeCampuses ?? rows.filter((c) => c.active).length}
                  label="Active" />
        <StatCard value={data?.inactiveCampuses ?? 0} label="Suspended"
                  hint="Read-only. Nothing is deleted." />
        <StatCard value={rows.reduce((n, c) => n + (c.activeGateCount ?? 0), 0)}
                  label="Gates" hint="Summed across campuses." />
      </StatRow>

      <div className="p-card">
        <div className="p-pad-sm"><span className="p-label">By campus</span></div>
        <DataTable
          columns={[
            { key: 'name', header: 'Campus', primary: true, sortable: true },
            { key: 'code', header: 'Code', render: (c) => <span className="p-mono">{c.code}</span> },
            { key: 'activeGateCount', header: 'Gates', sortable: true },
            { key: 'contactEmail', header: 'Contact' },
            { key: 'active', header: 'Status',
              render: (c) => <StatusBadge status={c.active ? 'ACTIVE' : 'PAUSED'}
                                          note={c.active ? undefined : 'suspended, read-only'} /> },
          ]}
          rows={rows} rowKey={(c) => c.code}
        />
      </div>

      <p className="p-caption">
        Suspending a campus stops new activity. Nothing is deleted and existing
        records stay readable.
      </p>
    </div>
  );
}
