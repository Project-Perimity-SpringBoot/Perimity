import { useState } from 'react';
import {
  DataTable, SearchFilterBar, StatusBadge, Button, Drawer, EmptyState,
} from '../shared/ui';
import { VISITOR_REQUESTS, DEPARTMENTS } from '../mock/data';

/**
 * Screen 7 — Faculty visitor approvals. WORKED EXAMPLE.
 *
 * Roughly 90 lines, and it is responsive, sortable, filterable, has an empty
 * state, a drawer that becomes a full-screen sheet on mobile, and matches
 * every other list screen in the product exactly.
 *
 * That is the whole argument for building Batch 0 first. Written directly
 * against raw HTML this is 400 lines and looks like nothing else.
 *
 * Swapping to the real API is one line — and note the path, because the
 * obvious guess is wrong. There is NO /visitor-requests/pending endpoint.
 * Status is a query parameter and the result is a PAGE, not an array:
 *
 *   const page = await gatepass.visitorRequests('PENDING');
 *   const rows = page.content;
 */
export default function ApprovalQueue() {
  const [search, setSearch] = useState('');
  const [dept, setDept] = useState('');
  const [selected, setSelected] = useState(null);

  const rows = VISITOR_REQUESTS.filter(
    (r) =>
      (!dept || r.dept === dept) &&
      (!search || `${r.name} ${r.email} ${r.code}`.toLowerCase().includes(search.toLowerCase())),
  );

  const columns = [
    {
      key: 'name', header: 'Visitor', primary: true, sortable: true,
      render: (r) => (
        <div>
          <div style={{ fontWeight: 600 }}>{r.name}</div>
          <div className="p-mono p-muted">{r.code}</div>
        </div>
      ),
    },
    {
      key: 'purpose', header: 'Purpose / Host',
      render: (r) => <div>{r.purpose}<div className="p-caption">{r.host} · {r.dept}</div></div>,
    },
    { key: 'dates', header: 'Dates', sortable: true },
    { key: 'status', header: 'Status', render: (r) => <StatusBadge status={r.status} /> },
  ];

  return (
    <div className="p-stack">
      <div className="p-spread">
        <h1 className="p-h1">Visitor approvals</h1>
        <Button variant="secondary">Export</Button>
      </div>

      <div className="p-card">
        <SearchFilterBar
          search={search} onSearch={setSearch}
          placeholder="Search name, email or pass code"
          filters={[{ key: 'dept', label: 'Department', value: dept,
                      options: DEPARTMENTS, onChange: setDept }]}
          count={rows.length} countLabel="pending"
        />
        <DataTable
          columns={columns} rows={rows} onRowClick={setSelected}
          empty={<EmptyState icon="✓" title="Nothing awaiting your review"
                             message="New visitor requests for your department will appear here." />}
        />
      </div>

      <Drawer
        open={!!selected} onClose={() => setSelected(null)}
        title={selected?.name}
        footer={
          <>
            <Button variant="secondary" onClick={() => setSelected(null)}>Reject</Button>
            <Button onClick={() => setSelected(null)}>Approve</Button>
          </>
        }
      >
        {selected && (
          <div className="p-stack">
            <dl className="p-pass__meta">
              <dt>Email</dt><dd>{selected.email}</dd>
              <dt>Purpose</dt><dd>{selected.purpose}</dd>
              <dt>Host</dt><dd>{selected.host} · {selected.dept}</dd>
              <dt>Dates</dt><dd>{selected.dates}</dd>
              <dt>Pass code</dt><dd className="p-mono">{selected.code}</dd>
            </dl>
            {/* Shown as an explicit line, always — "no news" is not an answer
                a reviewer can act on. */}
            <div className="p-card p-pad-sm">
              <span className="p-label">Blocklist</span>
              <div className="p-body">{selected.blocklist}</div>
            </div>
          </div>
        )}
      </Drawer>
    </div>
  );
}
