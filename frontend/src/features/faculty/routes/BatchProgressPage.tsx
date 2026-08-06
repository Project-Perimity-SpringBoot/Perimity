import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router';
import { CheckCircle2, FileSpreadsheet, RefreshCw, TriangleAlert, Sparkles, ArrowLeft } from 'lucide-react';
import { Button, Progress, SkeletonText } from '@ui/index';
import { ErrorState } from '@components/feedback';
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
    return <div className="rounded-3xl border border-slate-200 bg-white p-8"><SkeletonText lines={5} /></div>;
  }

  const data = batch.data;
  const running = RUNNING.has(data.status);
  const failed = data.status === 'FAILED';
  const done = data.status === 'COMPLETED';
  const stalled = Math.max(0, data.validRows - data.processedRows);

  return (
    <div className="mx-auto max-w-4xl space-y-6 pb-16">
      <Button variant="ghost" size="sm" asChild className="gap-2 text-slate-600 hover:text-slate-900">
        <Link to="/faculty/onboarding"><ArrowLeft className="size-4" /> Back to Bulk Onboarding</Link>
      </Button>

      {/* Hero Header */}
      <div className="relative overflow-hidden rounded-3xl border border-indigo-100 bg-gradient-to-r from-slate-900 via-indigo-950 to-slate-900 p-8 text-white shadow-xl">
        <div className="flex flex-wrap items-center justify-between gap-6">
          <div className="space-y-2">
            <div className="inline-flex items-center gap-2 rounded-full bg-white/10 px-3 py-1 text-xs font-semibold text-indigo-200 backdrop-blur-md border border-white/10">
              <Sparkles className="size-3.5 text-indigo-300" /> Batch Job Tracker
            </div>
            <h1 className="text-3xl font-extrabold text-white sm:text-4xl">{data.originalFilename}</h1>
            <p className="text-sm text-indigo-200/90">
              Batch #{data.id} · Uploaded {formatDateTime(data.createdAt)}
            </p>
          </div>

          <Button variant="secondary" asChild className="bg-white/10 text-white border-white/10 hover:bg-white/20">
            <Link to="/faculty/onboarding">New Batch</Link>
          </Button>
        </div>
      </div>

      {/* Main Status & Progress Container */}
      <div className="rounded-3xl border border-slate-200/80 bg-white p-8 shadow-sm space-y-6">
        <div className="space-y-2">
          <div className="flex items-center justify-between font-bold text-slate-900 text-sm">
            <span>Pass Generation Progress</span>
            <span className="font-mono text-indigo-600">{data.percentComplete}%</span>
          </div>
          <Progress
            value={data.percentComplete}
            label={`${data.processedRows} of ${data.validRows} passes generated`}
          />
        </div>

        {running && (
          <div className="rounded-2xl border border-indigo-100 bg-indigo-50/60 p-5 text-indigo-900 space-y-1">
            <p className="font-bold text-sm">Passes are actively being created & emailed</p>
            <p className="text-xs text-indigo-700">
              You can safely navigate away from this page. Processing continues in the background.
            </p>
          </div>
        )}

        {done && (
          <div className="flex items-start gap-3.5 rounded-2xl border border-emerald-200 bg-emerald-50 p-5 text-emerald-900">
            <CheckCircle2 className="size-5 shrink-0 text-emerald-600 mt-0.5" />
            <div className="space-y-1 text-xs">
              <p className="font-bold text-sm">
                Completed: All {data.processedRows} passes created & emailed successfully!
              </p>
              <p className="text-emerald-700">
                Finished {data.completedAt ? formatDateTime(data.completedAt) : 'just now'}.
                {data.invalidRows > 0 && ` ${data.invalidRows} rows with errors were skipped.`}
              </p>
            </div>
          </div>
        )}

        {failed && (
          <div className="flex items-start gap-3.5 rounded-2xl border border-rose-200 bg-rose-50 p-5 text-rose-900">
            <TriangleAlert className="size-5 shrink-0 text-rose-600 mt-0.5" />
            <div className="space-y-1 text-xs">
              <p className="font-bold text-sm">
                {data.processedRows > 0 ? `Stopped after generating ${data.processedRows} of ${data.validRows} passes.` : 'Batch processing encountered an issue.'}
              </p>
              <p className="text-rose-700">
                {data.processedRows > 0 ? `${data.processedRows} passes were generated & emailed. ${stalled} remain uncreated.` : 'No passes were generated.'}
              </p>
              {data.failureMessage && <p className="font-mono text-[11px] text-rose-800 mt-1">{data.failureMessage}</p>}
            </div>
          </div>
        )}

        <div className="flex flex-wrap gap-3 pt-2 border-t border-slate-100">
          {failed && stalled > 0 && (
            <Button
              onClick={() => retry.mutate()}
              loading={retry.isPending}
              className="gap-2 bg-indigo-600 font-semibold text-white shadow-md shadow-indigo-500/20 hover:bg-indigo-700"
            >
              <RefreshCw className="size-4" /> Retry Failed ({stalled})
            </Button>
          )}
          {data.invalidRows > 0 && (
            <Button
              variant="secondary"
              onClick={() => errorReport.mutate()}
              loading={errorReport.isPending}
              className="gap-2"
            >
              <FileSpreadsheet className="size-4 text-indigo-600" /> Export Error Report
            </Button>
          )}
          {done && data.eventId && (
            <Button variant="secondary" asChild>
              <Link to={`/faculty/events/${data.eventId}/attendance`}>View Event Attendance</Link>
            </Button>
          )}
        </div>
      </div>

      {/* Batch Stats grid */}
      <dl className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        <div className="rounded-2xl border border-slate-200/80 bg-white p-5 text-center shadow-sm">
          <dt className="text-xs font-semibold text-slate-500">Rows in Sheet</dt>
          <dd className="mt-1 text-2xl font-extrabold text-slate-900">{data.totalRows}</dd>
        </div>
        <div className="rounded-2xl border border-slate-200/80 bg-white p-5 text-center shadow-sm">
          <dt className="text-xs font-semibold text-emerald-600">Valid Rows</dt>
          <dd className="mt-1 text-2xl font-extrabold text-slate-900">{data.validRows}</dd>
        </div>
        <div className="rounded-2xl border border-slate-200/80 bg-white p-5 text-center shadow-sm">
          <dt className="text-xs font-semibold text-amber-600">Rows With Errors</dt>
          <dd className="mt-1 text-2xl font-extrabold text-slate-900">{data.invalidRows}</dd>
        </div>
        <div className="rounded-2xl border border-slate-200/80 bg-white p-5 text-center shadow-sm">
          <dt className="text-xs font-semibold text-indigo-600">Generated Passes</dt>
          <dd className="mt-1 text-2xl font-extrabold text-slate-900">{data.processedRows}</dd>
        </div>
      </dl>
    </div>
  );
}
