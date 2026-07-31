import { useParams, Link } from 'react-router-dom';
import { ProgressBar, StatCard, StatRow, Button, ErrorState } from '../shared/ui';
import { qr, gatepass, usePolling, useApi } from '../api';

/**
 * Screen 3 of 3 — watch 580 passes generate. LIVE, polling qr-service.
 *
 * TWO BARS, NOT ONE, and this is the important decision on this screen.
 *
 * Generation and delivery are different facts. A batch can be 100% generated
 * with three hundred people who were never emailed, and a single bar reading
 * "580 of 580 complete" is lying by omission — it says done when three hundred
 * people cannot get through a gate. Sanjay's BatchProgressResponse separates
 * emailsSent / emailsFailed / emailsPending precisely so this screen can.
 *
 * Polling stops when `finished` is true. A poller with no terminal condition
 * is a request every two seconds for as long as the tab is open.
 */
export default function BulkProgress() {
  const { batchId } = useParams();

  const { data: p, error } = usePolling(() => qr.batchProgress(batchId), {
    intervalMs: 2000,
    stop: (d) => d?.finished,
  });
  const { data: batch } = useApi(() => gatepass.batch(batchId), [batchId]);

  if (error && !p) return <ErrorState title="Could not reach qr-service" message={error.message} />;
  if (!p) return <ProgressBar indeterminate label="Starting…" />;

  const anythingWrong = p.failed > 0 || p.emailsFailed > 0;

  return (
    <div className="p-stack" style={{ maxWidth: 680 }}>
      <div>
        <h1 className="p-h1">{p.finished ? 'Batch complete' : 'Generating passes'}</h1>
        <p className="p-caption p-mono">
          Batch {batchId}{batch?.originalFilename ? ` · ${batch.originalFilename}` : ''}
        </p>
      </div>

      <div className="p-card p-pad">
        <div className="p-stack">
          <ProgressBar value={p.done} max={p.total} label="Passes generated" />
          <ProgressBar value={p.emailsSent} max={p.total} label="Emails delivered" />
        </div>
      </div>

      <StatRow>
        <StatCard value={p.done} label="Generated" />
        <StatCard value={p.emailsSent} label="Emailed" />
        <StatCard value={p.emailsPending} label="Email pending" />
        <StatCard value={p.failed + p.emailsFailed} label="Failed"
                  hint={anythingWrong ? 'Retry re-queues only the failures.' : undefined} />
      </StatRow>

      {!p.finished && (
        <p className="p-caption">
          Safe to leave this page — generation continues on the server. The
          batch also appears in the upload history.
        </p>
      )}

      {p.finished && (
        <div className="p-row">
          {anythingWrong && (
            <Button variant="secondary" onClick={() => gatepass.retryBatch(batchId)}>
              Retry {p.failed + p.emailsFailed} failures
            </Button>
          )}
          <Link to="/bulk/history"><Button variant="secondary">Upload history</Button></Link>
          <Link to="/bulk"><Button>Upload another sheet</Button></Link>
        </div>
      )}

      {p.finished && p.emailsFailed > 0 && (
        <div className="p-card p-pad-sm">
          <span className="p-label">{p.emailsFailed} people were not emailed</span>
          <p className="p-caption" style={{ marginBottom: 0 }}>
            Their passes exist and will scan. They just do not have them yet —
            retry, or send the PDFs individually from each pass.
          </p>
        </div>
      )}
    </div>
  );
}
