import { useState } from 'react';
import { AuditDiffRow, SearchFilterBar, Button } from '../shared/ui';
import { AUDIT } from '../mock/data';

/**
 * Screens 10 and 11 — the audit log, and a row expanded into a diff.
 *
 * Append-only. There is no edit and no delete anywhere in this screen, because
 * a log somebody can tidy is not evidence (FR-AUD-4).
 *
 * The diff uses struck-through grey and ink, not red and green: an audit entry
 * is a record, not a judgement, and those colours belong to the gate.
 */
export default function AuditLog({ scope = 'campus' }) {
  const [search, setSearch] = useState('');

  return (
    <div className="p-stack">
      <div>
        <h1 className="p-h1">{scope === 'platform' ? 'Platform audit' : 'Audit log'}</h1>
        <p className="p-caption">
          Every approval, rejection, edit and configuration change is recorded.
        </p>
      </div>

      <div className="p-card">
        <SearchFilterBar
          search={search} onSearch={setSearch} placeholder="Search actor or target"
          filters={[
            { key: 'a', label: 'Action', value: '', onChange: () => {},
              options: ['Approval', 'Rejection', 'Configuration', 'Pass revoked'] },
          ]}
          count={AUDIT.length} countLabel="entries"
          actions={<Button variant="secondary">Export CSV</Button>}
        />
        <div>
          {AUDIT.map((a) => <AuditDiffRow key={a.id} {...a} />)}
        </div>
      </div>
    </div>
  );
}
