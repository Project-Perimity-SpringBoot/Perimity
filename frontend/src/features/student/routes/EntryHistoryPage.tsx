import { useQuery } from '@tanstack/react-query';
import { ScanLine } from 'lucide-react';
import { SkeletonText } from '@ui/index';
import { EmptyState, ErrorState } from '@components/feedback';
import { PageHeader, Pagination } from '@components/data';
import { entryLogApi } from '@lib/api/services/guard.api';
import { guardKeys } from '@lib/query/keys';
import { formatDateTime } from '@lib/format/datetime';
import { flags } from '@lib/config';
import { useAuth } from '@hooks/useAuth';
import { useUrlPagination } from '@hooks/useUrlPagination';

/**
 * Phase 3 screen 5 — the student's own gate entries.
 *
 * ==========================================================================
 * SHIPS DARK BEHIND B9
 * ==========================================================================
 * The security matcher on guard-service admits GUARD, CAMPUS_ADMIN and
 * SUPER_ADMIN to /api/guard/entry-logs/** — a STUDENT gets 403. The screen is
 * finished and correct; it simply does not fire the request until
 * VITE_ENABLE_STUDENT_ENTRY_HISTORY is true.
 *
 * An honest empty state beats a 403 toast. The student did nothing wrong and a
 * permission error would read as though they had.
 *
 * ==========================================================================
 * ENTRY ROWS ONLY
 * ==========================================================================
 * Every row says "Entered". There is no exit scan anywhere in the product, so
 * a column for it would be a column that is always blank, and a reader would
 * reasonably conclude the data was missing rather than never collected.
 *
 * Denied attempts ARE shown. A student refused at a gate should be able to see
 * that it happened and why, rather than only hearing it from the guard.
 */
export default function EntryHistoryPage() {
  const { identity } = useAuth();
  const { request, setPage } = useUrlPagination(20);

  const entries = useQuery({
    queryKey: guardKeys.entryLogsByHolder(identity?.userId ?? 0, request),
    queryFn: () => entryLogApi.byHolder(identity?.userId as number, request),
    enabled: flags.studentEntryHistory && identity?.userId != null,
  });

  if (!flags.studentEntryHistory) {
    return (
      <div className="flex flex-col gap-[var(--sp-6)]">
        <PageHeader title="Entry history" description="Every time you were scanned at a gate." />
        <EmptyState
          icon={ScanLine}
          heading="Entry history is not available yet"
          description="Your gate entries are recorded, but students cannot read the register yet. This page will fill in once that opens."
        />
      </div>
    );
  }

  if (entries.isError) {
    return <ErrorState error={entries.error} onRetry={() => void entries.refetch()} />;
  }

  const page = entries.data;
  const rows = page?.items ?? [];

  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      <PageHeader
        title="Entry history"
        description="Every time you were scanned at a gate, newest first."
      />

      {entries.isPending ? (
        <div className="surface-card p-[var(--sp-6)]"><SkeletonText lines={8} /></div>
      ) : rows.length === 0 ? (
        <EmptyState
          icon={ScanLine}
          heading="No entries yet"
          description="Your first scan at a gate will show up here."
        />
      ) : (
        <>
          <ul className="surface-card divide-y divide-[var(--border)]">
            {rows.map((entry) => {
              const refused = entry.scanResult === 'DENIED';
              return (
                <li key={entry.id} className="flex flex-wrap items-baseline justify-between gap-[var(--sp-3)] p-[var(--sp-4)]">
                  <div className="min-w-0">
                    <p className="text-body text-[var(--ink-900)]">
                      {refused ? 'Refused at' : 'Entered at'} {entry.gateName}
                    </p>
                    {refused && entry.denialReason && (
                      <p className="text-caption mt-[var(--sp-1)] text-[var(--ink-500)]">
                        {entry.denialReason.replaceAll('_', ' ').toLowerCase()}
                      </p>
                    )}
                  </div>
                  <span className="text-small shrink-0 text-[var(--ink-500)]">
                    {formatDateTime(entry.scannedAt)}
                  </span>
                </li>
              );
            })}
          </ul>
          {page && <Pagination page={page} onPageChange={setPage} />}
        </>
      )}
    </div>
  );
}
