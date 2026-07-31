import { useState } from 'react';
import { DataTable, SearchFilterBar, StatusBadge } from '../shared/ui';
import { PASSES } from '../mock/data';

/** Screen 4 — every pass this identity has ever held. One person, many passes. */
export default function PassHistory() {
  const [type, setType] = useState('');
  const [status, setStatus] = useState('');

  const rows = PASSES.filter((p) =>
    (!type || p.type === type) && (!status || p.status === status));

  const columns = [
    { key: 'code', header: 'Pass code', primary: true, sortable: true,
      render: (r) => <span className="p-mono">{r.code}</span> },
    { key: 'type', header: 'Type',
      render: (r) => r.type.charAt(0).toUpperCase() + r.type.slice(1) },
    { key: 'validity', header: 'Valid' },
    { key: 'status', header: 'Status',
      render: (r) => <StatusBadge status={r.status} note={r.note} /> },
  ];

  return (
    <div className="p-stack">
      <h1 className="p-h1">Pass history</h1>
      <div className="p-card">
        <SearchFilterBar
          search="" onSearch={() => {}} placeholder="Search pass code"
          filters={[
            { key: 't', label: 'Type', value: type, onChange: setType,
              options: [{ value: 'daily', label: 'Daily' }, { value: 'event', label: 'Event' }] },
            { key: 's', label: 'Status', value: status, onChange: setStatus,
              options: ['PENDING', 'ACTIVE', 'PAUSED', 'EXPIRED', 'REVOKED'] },
          ]}
          count={rows.length} countLabel="passes"
        />
        <DataTable columns={columns} rows={rows} />
      </div>
    </div>
  );
}
