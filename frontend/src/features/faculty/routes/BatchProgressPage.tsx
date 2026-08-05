import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router';
import { CheckCircle2, FileSpreadsheet, RefreshCw, TriangleAlert } from 'lucide-react';
import { Button, Progress, SkeletonText } from '@ui/index';
import { PageHeader } from '@components/data';
import { ErrorState } from '@components/feedback';
import { bulkApi } from '@lib/api/services/gatepass.api';
import { bulkKeys } from '@lib/query/keys';
import { formatDateTime } from '@lib/format/datetime';
import { useToast } from '@hooks/useToast';
import type { BulkUploadBatchResponse } from '@/types/gatepass.types';

/** Still working. Anything else has stopped, one way or the other. */
const RUNNING: ReadonlySet<string> = new Set(['VALIDATING', 'PROCESSING']);

/**
 * Phase 4 screens 8 and 9 — generation progress, and done.
 *
 * ==========================================================================
 * THIS SCREEN HAS ITS OWN URL BECAUSE THE USER IS TOLD TO LEAVE IT
 * ==========================================================================
 * Generation is asynchronous and can take minutes for 580 passes. Telling
 * somebody to sit and watch a bar is a lie about how long it takes, so the
 * screen says plainly that they can close the page — which only works if the
 * page can be found again. Hence /faculty/onboarding/batches/:batchId, plus the
 * active-batches panel on the dashboard as the way back for anyone who did not
 * keep the URL.
 *
 * ==========================================================================
 * POLLING STOPS WHEN THE WORK DOES
 * ==========================================================================
 * refetchInterval returns false once the batch reaches COMPLETED or FAILED. A
 * screen left open overnight on a finished batch should not still be asking.
 */
export default function BatchProgressPage() {
  const { batchId } = useParams();
  const id = Number(batchId);
  const toast = useToast();
  const queryClient = useQueryClient();

  const batch = useQuery({
    queryKey: bulkKeys.batch(id),
    queryFn: () => bulkApi.getBatch(id),
    enabled: Number.isFinite(id) && id > 0,
    refetchInterval: (query) =>
      query.state.data && RUNNING.has(query.state.data.status) ? 3000 : false,
  });

  /**
   * Re-queues only the rows that failed. Not a re-upload: the identities that
   * succeeded already exist, and running the sheet again would either duplicate
   * them or fail on every one.
   */
  const retry = useMutation({
    mutationFn: () => bulkApi.retry(id),
    onSuccess: (result) => {
      void queryClient.invalidateQueries({ queryKey: bulkKeys.batch(id) });
      toast.success('Retrying', `${result.requeued} ${result.requeued === 1 ? 'row' : 'rows'} back in the queue.`);
    },
    onError: (error) => toast.fromError(error, 'Those rows could not be re-queued.'),
  });

  const errorReport = useMutation({
    mutationFn: () => bulkApi.errorReportUrl(id),
    onSuccess: (url) => window.open(url, '_blank', 'noopener'),
    onError: (error) => toast.fromError(error, 'The error report could not be fetched.'),
  });

  if (batch.isError) {
    return <ErrorState error={batch.error} onRetry={() => void batch.refetch()} />;
  }
  if (batch.isPending || !batch.data) {
    return <div className="surface-card p-[var(--sp-6)]"><SkeletonText lines={5} /></div>;
  }

  const data = batch.data;
  const running = RUNNING.has(data.status);
  const failed = data.status === 'FAILED';
  const done = data.status === 'COMPLETED';

  /* Passes that will never arrive: valid rows the generator could not finish.
     Distinct from invalidRows, which never entered the queue at all. */
  const stalled = Math.max(0, data.validRows - data.processedRows);

  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      <PageHeader
        title={data.originalFilename}
        description={`Uploaded ${formatDateTime(data.createdAt)}`}
        actions={
          <Button variant="secondary" asChild>
            <Link to="/faculty/onboarding">New batch</Link>
          </Button>
        }
      />

      <div className="surface-card flex flex-col gap-[var(--sp-4)] p-[var(--sp-6)]">
        <Progress
          value={data.percentComplete}
          label={`${data.processedRows} of ${data.validRows} generated`}
        />

        {running ? (
          <div className="flex flex-col gap-[var(--sp-2)]">
            <p className="text-body text-[var(--ink-900)]">
              Passes are being created and emailed.
            </p>
            {/* The sentence this whole screen's URL exists to make true. */}
            <p className="text-caption text-[var(--ink-500)]">
              You can close this page. The work carries on without it, and this batch
              stays on your dashboard until it finishes.
            </p>
          </div>
        ) : null}

        {done ? (
          <div className="flex items-start gap-[var(--sp-3)]">
            <CheckCircle2 className="mt-[2px] size-5 shrink-0 text-[var(--ink-700)]" aria-hidden />
            <div>
              <p className="text-body-md text-[var(--ink-900)]">
                All {data.processedRows} passes created and emailed.
              </p>
              <p className="text-caption text-[var(--ink-500)]">
                Finished {data.completedAt ? formatDateTime(data.completedAt) : 'just now'}.
                {data.invalidRows > 0
                  ? ` ${data.invalidRows} ${data.invalidRows === 1 ? 'row was' : 'rows were'} skipped for errors and nobody in them was contacted.`
                  : null}
              </p>
            </div>
          </div>
        ) : null}

        {failed ? (
          <div className="flex items-start gap-[var(--sp-3)]">
            <TriangleAlert className="mt-[2px] size-5 shrink-0 text-[var(--ink-700)]" aria-hidden />
            <div className="flex flex-col gap-[var(--sp-1)]">
              <p className="text-body-md text-[var(--ink-900)]">
                {data.processedRows > 0
                  ? `Stopped after ${data.processedRows} of ${data.validRows}.`
                  : 'This batch could not be started.'}
              </p>
              <p className="text-caption text-[var(--ink-500)]">
                {/* Partial failure is the normal failure here: the first 312
                    people have a working pass and the last 268 have nothing.
                    Saying "failed" without that split would be wrong in both
                    directions. */}
                {data.processedRows > 0
                  ? `Those ${data.processedRows} passes are valid and their holders have been emailed. The remaining ${stalled} have not been created.`
                  : 'Nothing was created, so nobody has been emailed.'}
              </p>
              {data.failureMessage ? (
                <p className="text-caption mt-[var(--sp-1)] text-[var(--ink-700)]">
                  {data.failureMessage}
                </p>
              ) : null}
            </div>
          </div>
        ) : null}

        <div className="flex flex-wrap gap-[var(--sp-3)]">
          {failed && stalled > 0 ? (
            <Button onClick={() => retry.mutate()} loading={retry.isPending}>
              <RefreshCw aria-hidden />Retry the {stalled} that did not finish
            </Button>
          ) : null}
          {data.invalidRows > 0 ? (
            <Button
              variant="secondary"
              onClick={() => errorReport.mutate()}
              loading={errorReport.isPending}
            >
              <FileSpreadsheet aria-hidden />Error report
            </Button>
          ) : null}
          {done && data.eventId ? (
            <Button variant="secondary" asChild>
              <Link to={`/faculty/events/${data.eventId}/attendance`}>Go to the event</Link>
            </Button>
          ) : null}
        </div>
      </div>

      <BatchFacts batch={data} />
    </div>
  );
}

/** total = valid + invalid, stated in one place so it cannot drift. */
function BatchFacts({ batch }: { batch: BulkUploadBatchResponse }) {
  return (
    <dl className="surface-card grid grid-cols-2 gap-[var(--sp-4)] p-[var(--sp-6)] sm:grid-cols-4">
      <Fact label="Rows in sheet" value={batch.totalRows} />
      <Fact label="Valid" value={batch.validRows} />
      <Fact label="With errors" value={batch.invalidRows} />
      <Fact label="Generated" value={batch.processedRows} />
    </dl>
  );
}

function Fact({ label, value }: { label: string; value: number }) {
  return (
    <div>
      <dt className="text-label text-[var(--ink-500)]">{label}</dt>
      <dd className="text-h3 text-[var(--ink-900)]">{value}</dd>
    </div>
  );
}
