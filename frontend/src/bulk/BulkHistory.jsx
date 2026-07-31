import { Link } from 'react-router-dom';
import { DataTable, StatusBadge, EmptyState, ErrorState, TableSkeleton, Button } from '../shared/ui';
import { gatepass, useApi } from '../api';

/**
 * Past uploads. LIVE.
 *
 * Exists because Bulk Progress is leaveable — if the only way back to a
 * running batch is a URL somebody still has open, then closing the tab loses
 * it. This is that way back.
 */
const BATCH_BADGE = {
  VALIDATED: 'PENDING', PROCESSING: 'ACTIVE', COMPLETED: 'ACTIVE',
  FAILED: 'REVOKED', CANCELLED: 'REVOKED',
};

export default function BulkHistory() {
  const { data, loading, error, reload } = useApi(() => gatepass.batches({ size: 50 }), []);

  if (loading) return <TableSkeleton rows={6} cols={5} />;
  if (error)   return <ErrorState title="Could not load upload history" message={error.message} onRetry={reload} />;

  const rows = data?.content ?? data ?? [];

  const columns = [
    { key: 'originalFilename', header: 'File', primary: true,
      render: (r) => <span className="p-mono">{r.originalFilename}</span> },
    { key: 'passType', header: 'Type' },
    { key: 'counts', header: 'Rows',
      render: (r) => `${r.validRows} valid · ${r.invalidRows} errors` },
    { key: 'percentComplete', header: 'Progress', sortable: true,
      render: (r) => `${r.percentComplete}%` },
    { key: 'status', header: 'Status',
      render: (r) => <StatusBadge status={BATCH_BADGE[r.status] ?? 'PENDING'} note={r.status.toLowerCase()} /> },
    { key: 'go', header: '',
      render: (r) => (
        <Link to={r.status === 'VALIDATED' ? `/bulk/${r.id}/review` : `/bulk/${r.id}/progress`}>
          <Button size="sm" variant="secondary">Open</Button>
        </Link>
      ) },
  ];

  return (
    <div className="p-stack">
      <div className="p-spread">
        <h1 className="p-h1">Upload history</h1>
        <Link to="/bulk"><Button>New upload</Button></Link>
      </div>
      <div className="p-card">
        <DataTable columns={columns} rows={rows}
                   empty={<EmptyState icon="⬚" title="No uploads yet"
                                      message="Bulk uploads you run appear here, including ones still generating."
                                      actionLabel="Upload a sheet" />} />
      </div>
    </div>
  );
}
