import { useState } from 'react';
import { Button, DataTable, Modal, FormField, StatusBadge } from '../shared/ui';
import { PLATFORM } from '../mock/data';

/**
 * Screens 2 and 3 — campuses, and create/edit.
 *
 * THE CAMPUS CODE CANNOT CHANGE AFTER CREATION. It is embedded in object
 * storage paths (campuses/north-campus/logo-….png) and in pass URLs, so
 * renaming it orphans every file that campus has ever uploaded. The edit form
 * shows it locked with that explanation rather than failing on save.
 *
 * Suspend, never delete (FR-ADM-10) — a suspended campus keeps all its data,
 * readable.
 */
export default function Campuses() {
  const [editing, setEditing] = useState(null);

  const columns = [
    { key: 'campus', header: 'Campus', primary: true, sortable: true },
    { key: 'code', header: 'Code', render: (r) => <span className="p-mono">{r.code}</span> },
    { key: 'users', header: 'Users', sortable: true, render: (r) => r.users.toLocaleString() },
    { key: 'status', header: 'Status',
      render: (r) => <StatusBadge status={r.status === 'Active' ? 'ACTIVE' : 'PAUSED'} /> },
  ];

  return (
    <div className="p-stack">
      <div className="p-spread">
        <h1 className="p-h1">Campuses</h1>
        <Button onClick={() => setEditing({})}>Add campus</Button>
      </div>

      <div className="p-card">
        <DataTable columns={columns} rows={PLATFORM.rows} rowKey={(r) => r.code}
                   onRowClick={setEditing} />
      </div>

      <Modal open={!!editing} onClose={() => setEditing(null)}
             title={editing?.code ? `Edit ${editing.campus}` : 'Add campus'}
             footer={<><Button variant="secondary" onClick={() => setEditing(null)}>Cancel</Button>
                       <Button onClick={() => setEditing(null)}>Save</Button></>}>
        <div className="p-stack">
          <FormField label="Campus name" required defaultValue={editing?.campus} />
          <FormField
            label="Campus code" required defaultValue={editing?.code}
            disabled={!!editing?.code}
            help={editing?.code
              ? 'Permanent. The code is part of every storage path and pass URL for this campus.'
              : 'Short, uppercase, permanent. It becomes part of every storage path for this campus.'}
          />
          <FormField label="Contact email" type="email" />
          <FormField label="Address" as="textarea" />
          {!editing?.code && (
            <div className="p-card p-pad-sm" style={{ background: 'var(--surface-subtle)' }}>
              <span className="p-label">First Campus Admin</span>
              <div className="p-stack" style={{ marginTop: 'var(--s-2)' }}>
                <FormField label="Name" required />
                <FormField label="Email" type="email" required
                           help="They receive a temporary password and must change it at first sign-in." />
              </div>
            </div>
          )}
        </div>
      </Modal>
    </div>
  );
}
