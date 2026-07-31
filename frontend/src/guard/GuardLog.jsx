import { useState } from 'react';
import { DataTable, SearchFilterBar, StatCard, StatRow, EmptyState,
         ErrorState, TableSkeleton, Button } from '../shared/ui';
import { guard, useApi } from '../api';
import { useAuth } from '../shared/AuthContext';

/**
 * The entry log. LIVE.
 *
 * ENTRY ONLY. No exit column, no duration, no direction. The product does not
 * scan people out, so a column implying it would be a lie in a table people
 * will export and quote.
 *
 * The 90-day cap is enforced by the backend and mirrored here so the user
 * learns about it while choosing the dates, not after a round trip.
 */
const iso = (d) => d.toISOString().slice(0, 19);
const daysAgo = (n) => { const d = new Date(); d.setDate(d.getDate() - n); d.setHours(0, 0, 0, 0); return d; };

export default function GuardLog() {
  const { user } = useAuth();
  const [days, setDays] = useState('7');
  const [verdict, setVerdict] = useState('');

  const filter = {
    campusId: user.campusId,
    from: iso(daysAgo(Number(days))),
    to: iso(new Date()),
    ...(verdict ? { scanResult: verdict } : {}),
  };

  const { data, loading, error, reload } =
    useApi(() => guard.searchEntries(filter, 0, 50), [days, verdict, user.campusId]);
  const { data: stats } = useApi(() => guard.entryStats(filter), [days, verdict, user.campusId]);

  const columns = [
    { key: 'scannedAt', header: 'Time', primary: true, sortable: true,
      render: (r) => new Date(r.scannedAt).toLocaleString() },
    { key: 'holderName', header: 'Person' },
    { key: 'gateName', header: 'Gate' },
    { key: 'passType', header: 'Pass' },
    { key: 'attributed', header: 'Event',
      render: (r) => (r.eventAttributed ? '✓ attributed' : '—') },
    { key: 'scanResult', header: 'Result',
      render: (r) => (r.scanResult === 'DENIED'
        ? <span className="p-badge">✕ Denied · {String(r.denialReason || '').replace(/_/g, ' ').toLowerCase()}</span>
        : <span className="p-badge">{r.scanResult === 'AMBER' ? '! Repeat' : '✓ Allowed'}</span>) },
  ];

  return (
    <div className="p-stack">
      <div>
        <h1 className="p-h1">Entry log</h1>
        <p className="p-caption">
          Entries only — Perimity does not scan people out. Records are kept for
          90 days.
        </p>
      </div>

      <StatRow>
        <StatCard value={stats?.entriesPermitted ?? '—'} label="Entries permitted" />
        <StatCard value={stats?.allowedCount ?? '—'} label="Allowed" />
        <StatCard value={stats?.amberCount ?? '—'} label="Repeat scans"
                  hint="Seen already that day. Still recorded as an entry." />
        <StatCard value={stats?.deniedCount ?? '—'} label="Denied" />
      </StatRow>

      <div className="p-card">
        <SearchFilterBar
          filters={[
            { key: 'd', label: 'Period', value: days, onChange: setDays,
              options: ['1', '7', '30', '90'] },
            { key: 'v', label: 'Result', value: verdict, onChange: setVerdict,
              options: ['ALLOWED', 'AMBER', 'DENIED'] },
          ]}
          count={data?.totalElements ?? 0} countLabel="entries"
          actions={<Button variant="secondary" onClick={reload}>Refresh</Button>}
        />
        {loading ? <TableSkeleton rows={8} cols={6} />
         : error ? <ErrorState title="Could not load the entry log"
                               message={error.message} onRetry={reload} />
         : <DataTable columns={columns} rows={data?.content ?? []}
                      empty={<EmptyState icon="⇢" title="No entries in this period"
                                         message="Widen the date range, or clear the result filter." />} />}
      </div>
    </div>
  );
}
