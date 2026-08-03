import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { Loader } from 'lucide-react';
import { Progress } from '@ui/index';
import { bulkApi } from '@lib/api/services/gatepass.api';
import { bulkKeys } from '@lib/query/keys';
import type { BulkUploadBatchResponse } from '@/types/gatepass.types';

/** Still doing work. COMPLETED and FAILED are finished; VALIDATED is waiting on a human. */
const RUNNING: ReadonlySet<string> = new Set(['VALIDATING', 'PROCESSING']);

/**
 * Active bulk batches. THE PANEL THAT MUST NOT BE SKIPPED.
 *
 * ==========================================================================
 * WITHOUT THIS THERE IS NO ROUTE BACK INTO A RUNNING BATCH
 * ==========================================================================
 * Generation is asynchronous and the progress screen explicitly tells the
 * faculty member they can close the page — which they will, because 580 passes
 * take minutes. The batch id lives only in that screen's URL. Close the tab and
 * the work is still running with no way back to it: no notification, no inbox,
 * nothing in the sidebar.
 *
 * This panel is that way back. It is the only one.
 *
 * VALIDATED is included even though nothing is running: a batch sitting at
 * VALIDATED is one somebody uploaded, walked away from, and never confirmed.
 * Those 580 passes do not exist and nobody is being told — so it is exactly the
 * thing the dashboard should surface, not hide because no thread is busy.
 *
 * Polls only while something is actually moving. A dashboard that polls forever
 * is a dashboard nobody can leave open.
 */
export function ActiveBatchesPanel() {
  const batches = useQuery({
    // No `sort` — Spring Data emits two ORDER BY clauses and the query fails.
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
    <section aria-labelledby="active-batches" className="surface-card p-[var(--sp-5)]">
      <h2 id="active-batches" className="text-h3 mb-[var(--sp-4)] text-[var(--ink-900)]">
        Batches in progress
      </h2>

      <ul className="flex flex-col gap-[var(--sp-4)]">
        {unfinished.map((batch) => (
          <li key={batch.id}>
            <BatchRow batch={batch} />
          </li>
        ))}
      </ul>
    </section>
  );
}

function BatchRow({ batch }: { batch: BulkUploadBatchResponse }) {
  const awaitingConfirm = batch.status === 'VALIDATED';

  return (
    <Link
      to={`/faculty/onboarding/batches/${batch.id}`}
      className="block rounded-[var(--r-md)] p-[var(--sp-3)] transition-colors
                 hover:bg-[var(--surface-sunken)]
                 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2
                 focus-visible:outline-[var(--brand-600)]"
    >
      <div className="mb-[var(--sp-2)] flex items-baseline justify-between gap-[var(--sp-4)]">
        <span className="text-body-md truncate text-[var(--ink-900)]">
          {batch.originalFilename}
        </span>
        <span className="text-caption shrink-0 text-[var(--ink-500)]">
          {awaitingConfirm
            ? 'Waiting for you to confirm'
            /* validRows, not totalRows: the invalid ones were never going to
               become passes, so counting them would promise more than arrives. */
            : `${batch.processedRows} of ${batch.validRows} generated`}
        </span>
      </div>

      {awaitingConfirm ? (
        <p className="text-caption text-[var(--ink-500)]">
          {batch.validRows} passes are ready to create. Nothing has been issued yet.
        </p>
      ) : (
        <div className="flex items-center gap-[var(--sp-3)]">
          {/* percentComplete is computed server-side. Do not recompute it. */}
          <Progress value={batch.percentComplete} className="flex-1" />
          <Loader className="size-4 shrink-0 animate-spin text-[var(--ink-500)]" aria-hidden />
        </div>
      )}
    </Link>
  );
}
