import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { Loader, Layers, ArrowRight } from 'lucide-react';
import { Progress } from '@ui/index';
import { bulkApi } from '@lib/api/services/gatepass.api';
import { bulkKeys } from '@lib/query/keys';
import type { BulkUploadBatchResponse } from '@/types/gatepass.types';

const RUNNING: ReadonlySet<string> = new Set(['VALIDATING', 'PROCESSING']);

export function ActiveBatchesPanel() {
  const batches = useQuery({
    queryKey: bulkKeys.list({ page: 0, size: 5 }),
    queryFn: () => bulkApi.history({ page: 0, size: 5 }),
    refetchInterval: (query) => {
      const items = query.state.data?.items ?? [];
      return items.some((batch) => RUNNING.has(batch.status)) ? 4000 : false;
    },
  });

  const unfinished = (batches.data?.items ?? []).filter(
    (batch) => RUNNING.has(batch.status) || batch.status === 'VALIDATED',
  );

  if (batches.isPending || unfinished.length === 0) return null;

  return (
    <section aria-labelledby="active-batches" className="rounded-3xl border border-slate-200/80 bg-white p-6 shadow-sm space-y-4">
      <div className="flex items-center gap-3 border-b border-slate-100 pb-3">
        <div className="flex size-9 items-center justify-center rounded-xl bg-indigo-50 text-indigo-600 border border-indigo-100">
          <Layers className="size-5" />
        </div>
        <div>
          <h2 id="active-batches" className="font-bold text-slate-900 text-base">
            Active Import & Pass Batches
          </h2>
          <p className="text-xs text-slate-500">Live batch progress and unconfirmed uploads.</p>
        </div>
      </div>

      <div className="grid gap-3">
        {unfinished.map((batch) => (
          <BatchRow key={batch.id} batch={batch} />
        ))}
      </div>
    </section>
  );
}

function BatchRow({ batch }: { batch: BulkUploadBatchResponse }) {
  const awaitingConfirm = batch.status === 'VALIDATED';

  return (
    <Link
      to={`/faculty/onboarding/batches/${batch.id}`}
      className="group flex flex-col gap-2 rounded-2xl border border-slate-200/80 bg-slate-50/60 p-4 transition-all hover:border-indigo-300 hover:bg-white hover:shadow-md"
    >
      <div className="flex items-center justify-between gap-4">
        <span className="font-bold text-slate-900 text-sm truncate group-hover:text-indigo-600 transition-colors">
          {batch.originalFilename}
        </span>
        <span className="shrink-0 font-mono text-xs font-semibold text-indigo-700 bg-indigo-50 px-2.5 py-1 rounded-md border border-indigo-100 flex items-center gap-1">
          {awaitingConfirm ? 'Awaiting Confirmation' : `${batch.processedRows} of ${batch.validRows} Generated`}
          <ArrowRight className="size-3 group-hover:translate-x-0.5 transition-transform" />
        </span>
      </div>

      {awaitingConfirm ? (
        <p className="text-xs text-slate-500">
          {batch.validRows} passes validated & ready to issue. Click to confirm.
        </p>
      ) : (
        <div className="flex items-center gap-3 pt-1">
          <Progress value={batch.percentComplete} className="flex-1" />
          <Loader className="size-4 shrink-0 animate-spin text-indigo-600" />
        </div>
      )}
    </Link>
  );
}
