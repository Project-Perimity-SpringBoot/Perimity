import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router';
import { FileSpreadsheet, RefreshCw } from 'lucide-react';
import { Button, Progress, SkeletonText } from '@ui/index';
import { PageHeader, StatCard } from '@components/data';
import { Alert, ErrorState } from '@components/feedback';
import { bulkApi } from '@lib/api/services/gatepass.api';
import { bulkKeys } from '@lib/query/keys';
import { formatDateTime } from '@lib/format/datetime';
import { useToast } from '@hooks/useToast';

const RUNNING: ReadonlySet<string> = new Set(['VALIDATING', 'PROCESSING']);

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
    return (
      <div className="surface-panel p-[var(--sp-6)]">
        <SkeletonText lines={5} />
      </div>
    );
  }

  const data = batch.data;
  const running = RUNNING.has(data.status);
  const failed = data.status === 'FAILED';
  const done = data.status === 'COMPLETED';
  const stalled = Math.max(0, data.validRows - data.processedRows);

  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      <PageHeader
        breadcrumbs={[
          { label: 'Faculty', to: '/faculty' },
          { label: 'Bulk onboarding', to: '/faculty/onboarding' },
          { label: data.originalFilename },
        ]}
        title={data.originalFilename}
        description={`Batch #${data.id} · uploaded ${formatDateTime(data.createdAt)}`}
        actions={
          <Button variant="secondary" asChild>
            <Link to="/faculty/onboarding">New batch</Link>
          </Button>
        }
      />

      <div className="surface-panel flex flex-col gap-[var(--sp-6)] p-[var(--sp-6)]">
        <div className="flex flex-col gap-[var(--sp-2)]">
          <div className="text-body-md flex items-center justify-between text-[var(--ink-900)]">
            <span>Pass generation</span>
            <span className="text-mono tabular-nums text-[var(--brand-600)]">
              {data.percentComplete}%
            </span>
          </div>
          <Progress
            value={data.percentComplete}
            label={`${data.processedRows} of ${data.validRows} passes generated`}
          />
        </div>

        {running && (
          <Alert tone="info" live={false} title="Passes are being created and emailed">
            You can navigate away — processing continues in the background.
          </Alert>
        )}

        {done && (
          <Alert
            tone="success"
            live={false}
            title={`All ${data.processedRows} passes created and emailed`}
          >
            Finished {data.completedAt ? formatDateTime(data.completedAt) : 'just now'}.
            {data.invalidRows > 0 && ` ${data.invalidRows} rows with errors were skipped.`}
          </Alert>
        )}

        {failed && (
          <Alert
            tone="danger"
            title={
              data.processedRows > 0
                ? `Stopped after generating ${data.processedRows} of ${data.validRows} passes`
                : 'This batch could not be processed'
            }
          >
            {data.processedRows > 0
              ? `${data.processedRows} passes were generated and emailed. ${stalled} remain uncreated.`
              : 'No passes were generated.'}
            {data.failureMessage && (
              <span className="text-mono mt-[var(--sp-1)] block">{data.failureMessage}</span>
            )}
          </Alert>
        )}

        {(failed || data.invalidRows > 0 || (done && data.eventId)) && (
          <div className="flex flex-wrap gap-[var(--sp-2)] border-t border-[var(--border)] pt-[var(--sp-4)]">
            {failed && stalled > 0 && (
              <Button onClick={() => retry.mutate()} loading={retry.isPending}>
                <RefreshCw aria-hidden /> Retry failed ({stalled})
              </Button>
            )}
            {data.invalidRows > 0 && (
              <Button
                variant="secondary"
                onClick={() => errorReport.mutate()}
                loading={errorReport.isPending}
              >
                <FileSpreadsheet aria-hidden /> Export error report
              </Button>
            )}
            {done && data.eventId && (
              <Button variant="secondary" asChild>
                <Link to={`/faculty/events/${data.eventId}/attendance`}>View event attendance</Link>
              </Button>
            )}
          </div>
        )}
      </div>

      <div className="grid gap-[var(--sp-4)] sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label="Rows in sheet" value={data.totalRows} />
        <StatCard label="Valid rows" value={data.validRows} />
        <StatCard label="Rows with errors" value={data.invalidRows} />
        <StatCard label="Passes generated" value={data.processedRows} />
      </div>
    </div>
  );
}
