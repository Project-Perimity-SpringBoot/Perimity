import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { IdCard } from 'lucide-react';
import { NativeSelect, SkeletonText } from '@ui/index';
import { EmptyState, ErrorState } from '@components/feedback';
import { PageHeader } from '@components/data';
import { PassCard } from '@components/pass';
import { passApi } from '@lib/api/services/gatepass.api';
import { passKeys } from '@lib/query/keys';
import { PASS_STATUSES, type PassStatus } from '@/types/enums';
import { PausedBanner } from '../components/PausedBanner';

const isStatus = (v: string): v is PassStatus => (PASS_STATUSES as readonly string[]).includes(v);

/**
 * Phase 3 screen 4 — every pass the student has ever held.
 *
 * Filtered client-side. /api/gatepass/passes/mine returns the student's whole
 * list in one response and a person accumulates a handful of passes over a
 * degree, not thousands — so paging it server-side would add a round trip and a
 * spinner to save nothing.
 *
 * Expired and revoked passes stay on the list. They are the answer to "was I
 * allowed in last March", and deleting them would make the history a lie.
 */
export default function PassHistoryPage() {
  const [status, setStatus] = useState('');

  const passes = useQuery({
    queryKey: passKeys.mine(),
    queryFn: () => passApi.mine(),
  });

  const all = useMemo(() => passes.data ?? [], [passes.data]);

  const visible = useMemo(
    () => (isStatus(status) ? all.filter((p) => p.status === status) : all),
    [all, status],
  );

  if (passes.isError) {
    return <ErrorState error={passes.error} onRetry={() => void passes.refetch()} />;
  }

  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      <PausedBanner passes={all} />

      <PageHeader
        title="Pass history"
        description="Every pass issued to you, including ones that have expired."
      />

      <div className="flex items-center gap-[var(--sp-3)]">
        <label htmlFor="status-filter" className="text-small text-[var(--ink-700)]">
          Show
        </label>
        <NativeSelect
          id="status-filter"
          className="max-w-56"
          value={status}
          onChange={(e) => setStatus(e.target.value)}
        >
          <option value="">All passes</option>
          {PASS_STATUSES.map((s) => (
            <option key={s} value={s}>{s.charAt(0) + s.slice(1).toLowerCase()}</option>
          ))}
        </NativeSelect>
        {!passes.isPending && (
          <span className="text-caption text-[var(--ink-500)]">
            {visible.length} of {all.length}
          </span>
        )}
      </div>

      {passes.isPending ? (
        <div className="surface-card p-[var(--sp-6)]"><SkeletonText lines={6} /></div>
      ) : all.length === 0 ? (
        <EmptyState
          icon={IdCard}
          heading="You have never held a pass"
          description="Passes are issued by your department or when you register for an event."
        />
      ) : visible.length === 0 ? (
        <EmptyState
          icon={IdCard}
          heading="Nothing with that status"
          description="Change the filter to see your other passes."
        />
      ) : (
        <div className="grid gap-[var(--sp-4)] sm:grid-cols-2">
          {visible.map((pass) => (
            <Link
              key={pass.id}
              to={`/student/passes/${pass.id}`}
              className="rounded-[var(--r-md)] focus-visible:outline focus-visible:outline-2
                         focus-visible:outline-offset-2 focus-visible:outline-[var(--brand-600)]"
            >
              <PassCard pass={pass} />
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
