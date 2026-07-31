import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Button, DataTable, StatCard, StatRow, ErrorState, Modal,
         DetailSkeleton } from '../shared/ui';
import { gatepass, useApi } from '../api';

/**
 * Screen 2 of 3 — "580 valid, 20 errors. Confirm?" LIVE.
 *
 * THE ARITHMETIC MUST RECONCILE ON SCREEN. total = valid + invalid, shown as
 * three numbers the reader can add up themselves. A summary that says "580
 * ready" without saying what happened to the other 20 is the version people
 * click through, and then twenty people arrive at a gate with no pass.
 *
 * Confirm is destructive-adjacent: it issues passes and emails every holder.
 * It gets a confirmation dialog stating the count and that emails will go out.
 */
export default function BulkReview() {
  const { batchId } = useParams();
  const nav = useNavigate();
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);

  const { data: batch, loading, error, reload } = useApi(() => gatepass.batch(batchId), [batchId]);
  if (loading) return <DetailSkeleton />;
  if (error)   return <ErrorState title="Could not load this batch" message={error.message} onRetry={reload} />;

  const confirm = async () => {
    setBusy(true);
    try {
      await gatepass.confirmBatch(batchId);
      nav(`/bulk/${batchId}/progress`);
    } finally {
      setBusy(false);
      setConfirming(false);
    }
  };

  const downloadErrors = async () => {
    // A short-lived signed link, not the bytes. The bucket stays private.
    const { url } = await gatepass.errorReport(batchId);
    window.open(url, '_blank', 'noopener');
  };

  const reconciles = batch.totalRows === batch.validRows + batch.invalidRows;

  return (
    <div className="p-stack">
      <div>
        <h1 className="p-h1">Review upload</h1>
        <p className="p-caption p-mono">{batch.originalFilename}</p>
      </div>

      <StatRow>
        <StatCard value={batch.totalRows} label="Rows uploaded" />
        <StatCard value={batch.validRows} label="Valid" hint="These become passes." />
        <StatCard value={batch.invalidRows} label="Errors"
                  hint="Skipped. Downloadable as a report." />
      </StatRow>

      {!reconciles && (
        <div className="p-card p-pad-sm">
          <span className="p-label">Counts do not add up</span>
          <p className="p-caption" style={{ marginBottom: 0 }}>
            {batch.totalRows} uploaded but {batch.validRows} + {batch.invalidRows} ={' '}
            {batch.validRows + batch.invalidRows} accounted for. Do not confirm —
            report this rather than issuing a partial batch.
          </p>
        </div>
      )}

      {batch.invalidRows > 0 && (
        <div className="p-card p-pad">
          <div className="p-spread">
            <div>
              <span className="p-label">Error report</span>
              <p className="p-caption" style={{ marginBottom: 0, maxWidth: '52ch' }}>
                One row per rejection, with the row number so it can be fixed in
                the original sheet. Rows refused because the person is on the
                campus blocklist say only <strong>Refused</strong> — no reason.
                Publishing the reason would leak the blocklist to whoever opens
                the file.
              </p>
            </div>
            <Button variant="secondary" onClick={downloadErrors}>Download CSV</Button>
          </div>
        </div>
      )}

      <div className="p-row">
        <Button variant="secondary" onClick={() => nav('/bulk')}>Cancel</Button>
        <Button onClick={() => setConfirming(true)} disabled={!batch.validRows || !reconciles}>
          Issue {batch.validRows} passes
        </Button>
      </div>

      <Modal
        open={confirming} onClose={() => setConfirming(false)} title="Confirm issue"
        footer={<>
          <Button variant="secondary" onClick={() => setConfirming(false)}>Back</Button>
          <Button onClick={confirm} loading={busy}>Issue and email</Button>
        </>}>
        <p>
          This issues <strong>{batch.validRows} passes</strong> and emails each
          holder their QR code. It cannot be undone in bulk — passes would have
          to be revoked one at a time.
        </p>
        <p className="p-caption" style={{ marginBottom: 0 }}>
          The {batch.invalidRows} error rows are skipped and are not emailed.
        </p>
      </Modal>
    </div>
  );
}
