import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { ArrowRight, Layers, Loader } from 'lucide-react';
import { Badge, Progress } from '@ui/index';
import { SectionHeader } from '@components/data';
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
    <section aria-labelledby="active-batches" className="flex flex-col gap-[var(--sp-4)]">
      <SectionHeader
        id="active-batches"
        icon={Layers}
        title="Active batches"
        description="Uploads still running or waiting on your confirmation."
        divided
      />

      <div className="grid gap-[var(--sp-3)]">
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
      className="surface-card lift group flex flex-col gap-[var(--sp-2)] p-[var(--sp-4)]"
    >
      <div className="flex items-center justify-between gap-[var(--sp-4)]">
        <span className="text-body-md min-w-0 truncate text-[var(--ink-900)]">
          {batch.originalFilename}
        </span>
        <span className="flex shrink-0 items-center gap-[var(--sp-2)]">
          <Badge tone={awaitingConfirm ? 'brand' : 'neutral'}>
            {awaitingConfirm
              ? 'Awaiting confirmation'
              : `${batch.processedRows} of ${batch.validRows} generated`}
          </Badge>
          <ArrowRight
            className="size-4 text-[var(--ink-400)] transition-transform duration-[var(--motion-fast)] group-hover:translate-x-0.5"
            aria-hidden
          />
        </span>
      </div>

      {awaitingConfirm ? (
        <p className="text-small text-[var(--ink-500)]">
          {batch.validRows} passes validated and ready to issue. Open to confirm.
        </p>
      ) : (
        <div className="flex items-center gap-[var(--sp-3)]">
          <Progress value={batch.percentComplete} className="flex-1" />
          <Loader className="size-4 shrink-0 animate-spin text-[var(--brand-600)]" aria-hidden />
        </div>
      )}
    </Link>
  );
}
