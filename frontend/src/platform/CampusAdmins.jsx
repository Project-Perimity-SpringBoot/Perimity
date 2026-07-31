import { Button, DataTable, StatusBadge } from '../shared/ui';

/**
 * Screen 4 — campus admin accounts across the platform.
 *
 * The warning row is the whole point of this screen. A campus with zero active
 * admins is a campus nobody can administer, and it fails silently until
 * somebody tries. Surfacing it here costs one row; discovering it from a
 * support message costs a day.
 */
const ROWS = [
  { id: 1, name: 'Dr. S. Verma', email: 's.verma@example.com', campus: 'Main Campus',  last: 'Today 09:12', status: 'ACTIVE' },
  { id: 2, name: 'Dr. K. Nair',  email: 'k.nair@example.com',  campus: 'North Campus', last: 'Yesterday',   status: 'ACTIVE' },
  { id: 3, name: '—',            email: '—',                   campus: 'Lakeside Campus', last: '—',        status: 'NONE' },
];

export default function CampusAdmins() {
  const columns = [
    { key: 'name', header: 'Name', primary: true, sortable: true },
    { key: 'email', header: 'Email', render: (r) => <span className="p-mono">{r.email}</span> },
    { key: 'campus', header: 'Campus', sortable: true },
    { key: 'last', header: 'Last active' },
    { key: 'status', header: 'Status',
      render: (r) => r.status === 'NONE'
        ? <span className="p-badge">⚠ No active admin</span>
        : <StatusBadge status={r.status} /> },
  ];

  return (
    <div className="p-stack">
      <div className="p-spread">
        <div>
          <h1 className="p-h1">Campus admins</h1>
          <p className="p-caption">
            A campus with no active admin cannot approve requests or change its
            own configuration.
          </p>
        </div>
        <Button>Add admin</Button>
      </div>
      <div className="p-card"><DataTable columns={columns} rows={ROWS} /></div>
    </div>
  );
}
