import { DataTable, StatCard, StatRow, StatusBadge } from '../shared/ui';
import { PLATFORM, TODAY } from '../mock/data';

/**
 * Screen 1 — the Super Admin console.
 *
 * This role operates ACROSS campuses. It never approves a visitor request and
 * never manages a student. The shell renders with tone="platform" — a darker
 * bar and a scope chip — because the two admin consoles sit side by side in
 * somebody's browser tabs and suspending the wrong campus is unrecoverable.
 */
export default function PlatformOverview() {
  const columns = [
    { key: 'campus', header: 'Campus', primary: true, sortable: true,
      render: (r) => (
        <div>{r.campus}<div className="p-mono p-dim">{r.code}</div></div>
      ) },
    { key: 'users', header: 'Users', sortable: true, render: (r) => r.users.toLocaleString() },
    { key: 'passes', header: 'Active passes', sortable: true },
    { key: 'entries', header: 'Entries today' },
    { key: 'status', header: 'Status',
      render: (r) => <StatusBadge status={r.status === 'Active' ? 'ACTIVE' : 'PAUSED'}
                                  note={r.status === 'Suspended' ? 'suspended, read-only' : undefined} /> },
  ];

  return (
    <div className="p-stack">
      <div>
        <h1 className="p-h1">Platform overview</h1>
        <p className="p-caption">{TODAY} · all campuses</p>
      </div>

      <StatRow>
        <StatCard value={PLATFORM.campuses} label="Campuses" />
        <StatCard value={PLATFORM.users.toLocaleString()} label="Total users" />
        <StatCard value={PLATFORM.activePasses.toLocaleString()} label="Active passes" />
        <StatCard value={PLATFORM.servicesHealthy} label="Services healthy"
                  hint="All six microservices reporting UP." />
      </StatRow>

      <div className="p-card">
        <div className="p-pad-sm"><span className="p-label">By campus</span></div>
        <DataTable columns={columns} rows={PLATFORM.rows} rowKey={(r) => r.code} />
      </div>
    </div>
  );
}
