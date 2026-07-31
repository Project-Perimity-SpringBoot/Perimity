import { useState } from 'react';
import { Button, DataTable, Modal, FormField } from '../shared/ui';
import { DEPARTMENTS } from '../mock/data';

/**
 * Screen 5 — the screen that seeds departments for this campus.
 *
 * Departments are CAMPUS-SUPPLIED DATA, never a hardcoded list. That is the
 * multi-tenant claim in practice: two campuses can have entirely different
 * departments, and the product ships with none.
 *
 * Deactivate rather than delete — FR-ADM-8. A department referenced by an
 * existing profile or pass cannot be removed without orphaning it, so the only
 * safe operation is marking it inactive.
 */
export default function Departments() {
  const [adding, setAdding] = useState(false);
  const rows = DEPARTMENTS.map((name, i) => ({
    id: i, name, code: name.split(' ').map((w) => w[0]).join('').toUpperCase(),
    head: i === 0 ? 'Dr. Anaya Rao' : '—', users: [820, 640, 590, 410, 300][i],
  }));

  const columns = [
    { key: 'name', header: 'Department', primary: true, sortable: true },
    { key: 'code', header: 'Code', render: (r) => <span className="p-mono">{r.code}</span> },
    { key: 'head', header: 'Head' },
    { key: 'users', header: 'Users', sortable: true },
  ];

  return (
    <div className="p-stack">
      <div className="p-spread">
        <div>
          <h1 className="p-h1">Departments</h1>
          <p className="p-caption">
            Departments belong to this campus. Deactivating one does not delete its users.
          </p>
        </div>
        <Button onClick={() => setAdding(true)}>Add department</Button>
      </div>

      <div className="p-card"><DataTable columns={columns} rows={rows} /></div>

      <Modal open={adding} onClose={() => setAdding(false)} title="Add department"
             footer={<><Button variant="secondary" onClick={() => setAdding(false)}>Cancel</Button>
                       <Button onClick={() => setAdding(false)}>Add</Button></>}>
        <div className="p-stack">
          <FormField label="Department name" required placeholder="e.g. Civil Engineering" />
          <FormField label="Short code" required placeholder="e.g. CIVIL"
                     help="Used in listings and exports. Letters and digits only." />
        </div>
      </Modal>
    </div>
  );
}
