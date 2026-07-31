import { useState } from 'react';
import { Button, DataTable, SearchFilterBar, StatusBadge, Modal, FormField } from '../shared/ui';
import { DEPARTMENTS } from '../mock/data';

/**
 * Screens 3 and 4 — accounts on this campus.
 *
 * Deactivate, never delete (FR-ADM-6) — an account that vanishes takes its
 * audit trail and its pass history with it.
 *
 * Email, role and campus are permanent and the form says so BEFORE creation,
 * not in an error afterwards. Email is the universal key across all six
 * services; changing it would orphan the person's profile, passes and entries.
 */
const ROWS = [
  { id: 1, name: 'Dr. Anaya Rao', role: 'FACULTY', dept: 'Computer Science', last: 'Today 09:41', status: 'ACTIVE' },
  { id: 2, name: 'Sneha Kulkarni', role: 'STUDENT', dept: 'Data Science', last: 'Today 09:38', status: 'ACTIVE' },
  { id: 3, name: 'R. Singh', role: 'GUARD', dept: '—', last: 'Today 08:00', status: 'ACTIVE' },
  { id: 4, name: 'M. Fernandes', role: 'FACULTY', dept: 'Mechanical', last: '2 Jun 2026', status: 'EXPIRED' },
];

export default function UserManagement() {
  const [search, setSearch] = useState('');
  const [role, setRole] = useState('');
  const [adding, setAdding] = useState(false);

  const rows = ROWS.filter((r) =>
    (!role || r.role === role) &&
    (!search || r.name.toLowerCase().includes(search.toLowerCase())));

  const columns = [
    { key: 'name', header: 'Name', primary: true, sortable: true },
    { key: 'role', header: 'Role' },
    { key: 'dept', header: 'Department' },
    { key: 'last', header: 'Last active' },
    { key: 'status', header: 'Status',
      render: (r) => <StatusBadge status={r.status} note={r.status === 'EXPIRED' ? 'deactivated' : undefined} /> },
  ];

  return (
    <div className="p-stack">
      <div className="p-spread">
        <h1 className="p-h1">Users</h1>
        <Button onClick={() => setAdding(true)}>Add user</Button>
      </div>

      <div className="p-card">
        <SearchFilterBar
          search={search} onSearch={setSearch}
          placeholder="Search name, email or roll number"
          filters={[{ key: 'r', label: 'Role', value: role, onChange: setRole,
                      options: ['FACULTY', 'STUDENT', 'GUARD'] }]}
          count={rows.length} countLabel="accounts"
        />
        <DataTable columns={columns} rows={rows} />
      </div>

      <Modal open={adding} onClose={() => setAdding(false)} title="Add user"
             footer={<><Button variant="secondary" onClick={() => setAdding(false)}>Cancel</Button>
                       <Button onClick={() => setAdding(false)}>Create account</Button></>}>
        <div className="p-stack">
          <FormField label="Full name" required />
          <FormField label="Email" type="email" required
                     help="Permanent. Email is this person's identity across Perimity." />
          <FormField label="Role" as="select" required options={['FACULTY', 'STUDENT', 'GUARD']}
                     help="Permanent. Create a new account to change someone's role." />
          <FormField label="Department" as="select" options={['—', ...DEPARTMENTS]} />
          <FormField label="Phone" type="tel" />
          <p className="p-caption" style={{ margin: 0 }}>
            The account is created with a temporary password. They must change it
            at first sign-in.
          </p>
        </div>
      </Modal>
    </div>
  );
}
