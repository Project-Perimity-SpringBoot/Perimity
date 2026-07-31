import { useState } from 'react';
import { DataTable, FileDropzone, StatusBadge } from '../shared/ui';

/**
 * Screen 8 — document upload and review status.
 *
 * A rejected document must show its reason. "Rejected" alone means the student
 * uploads the same file again, and the reviewer rejects it again.
 */
const DOCS = [
  { id: 1, name: 'government-id.pdf', type: 'Government ID', at: '2 Jul 2026', status: 'ACTIVE',  label: 'Verified' },
  { id: 2, name: 'address-proof.pdf', type: 'Address proof', at: '2 Jul 2026', status: 'PENDING', label: 'Pending review' },
  { id: 3, name: 'photo.jpg',         type: 'Photo',         at: '6 Jul 2026', status: 'REVOKED', label: 'Rejected',
    reason: 'Face is not clearly visible. Please upload a front-facing photo.' },
];

export default function Documents() {
  const [file, setFile] = useState(null);
  const [error, setError] = useState(null);

  const columns = [
    { key: 'name', header: 'File', primary: true,
      render: (r) => <div><div>{r.name}</div><div className="p-caption">{r.type}</div></div> },
    { key: 'at', header: 'Uploaded' },
    { key: 'status', header: 'Status',
      render: (r) => (
        <div>
          <StatusBadge status={r.status} />
          {r.reason && <div className="p-caption" style={{ maxWidth: 260 }}>{r.reason}</div>}
        </div>
      ) },
  ];

  return (
    <div className="p-stack">
      <h1 className="p-h1">Documents</h1>

      <FileDropzone
        accept=".pdf,.jpg,.jpeg,.png" maxMb={5} file={file} error={error}
        hint="PDF, JPEG or PNG · maximum 5 MB"
        onFile={(f, err) => { setFile(f); setError(err); }}
      />

      <div className="p-card">
        <DataTable columns={columns} rows={DOCS} />
      </div>
    </div>
  );
}
