import { useState } from 'react';
import { Button, DataTable, EmptyState, Modal, FormField } from '../shared/ui';

/**
 * Screen 8 — the campus blocklist.
 *
 * A REASON IS MANDATORY. FR-BLK-1 requires it; the practical version is that
 * an entry with no reason cannot be defended six months later when somebody
 * asks why a person was refused at the gate.
 *
 * Blocklists are per-campus. Barred at one campus is not barred at another,
 * and the copy says so, because the word "blocklist" sounds global and is not.
 */
const ROWS = [
  { id: 1, identity: 'blocked@example.com', reason: 'Repeated no-show after approval',
    by: 'Dr. S. Verma', on: '2 Jun 2026', expires: 'Permanent' },
  { id: 2, identity: '+91 90000 00000', reason: 'Security incident, 12 May',
    by: 'Dr. S. Verma', on: '12 May 2026', expires: '12 May 2027' },
];

export default function Blocklist() {
  const [adding, setAdding] = useState(false);
  const [reason, setReason] = useState('');

  const columns = [
    { key: 'identity', header: 'Email or phone', primary: true,
      render: (r) => <span className="p-mono">{r.identity}</span> },
    { key: 'reason', header: 'Reason' },
    { key: 'by', header: 'Added by' },
    { key: 'on', header: 'Added on', sortable: true },
    { key: 'expires', header: 'Expires' },
  ];

  return (
    <div className="p-stack">
      <div className="p-spread">
        <div>
          <h1 className="p-h1">Blocklist</h1>
          <p className="p-caption">
            Blocked identities are skipped during bulk upload and refused at
            request time. This list applies to this campus only.
          </p>
        </div>
        <Button onClick={() => setAdding(true)}>Add to blocklist</Button>
      </div>

      <div className="p-card">
        <DataTable
          columns={columns} rows={ROWS}
          empty={<EmptyState icon="○" title="Nobody is blocked"
                             message="Entries added here are checked on every registration and every bulk-upload row." />}
        />
      </div>

      <Modal
        open={adding} onClose={() => setAdding(false)} title="Add to blocklist"
        footer={<>
          <Button variant="secondary" onClick={() => setAdding(false)}>Cancel</Button>
          <Button disabled={reason.trim().length < 5}
                  onClick={() => { setAdding(false); setReason(''); }}>Add</Button>
        </>}>
        <div className="p-stack">
          <FormField label="Email or phone" required placeholder="name@example.com" />
          <FormField label="Reason" as="textarea" required value={reason}
                     onChange={(e) => setReason(e.target.value)}
                     help="Recorded in the audit log. The blocked person is never shown this." />
          {/* FR-BLK-4: the refusal a blocked person sees is deliberately vague.
              Saying so here stops an admin assuming the reason becomes public. */}
          <p className="p-caption" style={{ margin: 0 }}>
            A blocked registration is refused with a non-specific message that
            does not reveal the block or its reason.
          </p>
        </div>
      </Modal>
    </div>
  );
}
