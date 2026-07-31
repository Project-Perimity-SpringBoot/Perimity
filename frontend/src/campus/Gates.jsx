import { useState } from 'react';
import { Button, Modal, FormField, StatusBadge } from '../shared/ui';
import { GATES, CAMPUS } from '../mock/data';

/**
 * Screens 6 and 7 — gate configuration.
 *
 * THERE IS NO "RE-ENTRY ALLOWED" TOGGLE. Entry-only makes it meaningless: a
 * person may enter many times a day and each entry is its own row. What the
 * campus can configure is what a repeat entry SHOWS the guard, and that lives
 * in campus policy as repeat_entry_result, not here.
 */
export default function Gates() {
  const [editing, setEditing] = useState(null);

  return (
    <div className="p-stack">
      <div className="p-spread">
        <div>
          <h1 className="p-h1">Gates</h1>
          <p className="p-caption">{CAMPUS.name} — {GATES.length} gates configured</p>
        </div>
        <Button onClick={() => setEditing({})}>Add gate</Button>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(260px,1fr))', gap: 'var(--s-4)' }}>
        {GATES.map((g) => (
          <div key={g.id} className="p-card p-pad p-stack" style={{ gap: 'var(--s-2)' }}>
            <div className="p-spread">
              <span className="p-h3">{g.name}</span>
              <StatusBadge status={g.active ? 'ACTIVE' : 'EXPIRED'} />
            </div>
            <span className="p-caption">{g.location}</span>
            <dl className="p-pass__meta">
              <dt>Type</dt><dd>{g.type}</dd>
              <dt>Hours</dt><dd className="p-mono">06:00 – 22:00</dd>
              <dt>Gate ID</dt><dd className="p-mono">{g.id}</dd>
            </dl>
            <Button variant="secondary" onClick={() => setEditing(g)}>Edit</Button>
          </div>
        ))}
      </div>

      <Modal open={!!editing} onClose={() => setEditing(null)}
             title={editing?.id ? `Edit ${editing.name}` : 'Add gate'}
             footer={<><Button variant="secondary" onClick={() => setEditing(null)}>Cancel</Button>
                       <Button onClick={() => setEditing(null)}>Save</Button></>}>
        <div className="p-stack">
          <FormField label="Gate name" required defaultValue={editing?.name} />
          <FormField label="Location note" defaultValue={editing?.location} />
          <FormField label="Type" as="select" defaultValue={editing?.type}
                     options={['Vehicle + pedestrian', 'Pedestrian only', 'Vendor / logistics']} />
          <div className="p-row" style={{ alignItems: 'flex-start' }}>
            <div className="p-grow"><FormField label="Active from" type="time" defaultValue="06:00" /></div>
            <div className="p-grow"><FormField label="Active to" type="time" defaultValue="22:00" /></div>
          </div>
        </div>
      </Modal>
    </div>
  );
}
