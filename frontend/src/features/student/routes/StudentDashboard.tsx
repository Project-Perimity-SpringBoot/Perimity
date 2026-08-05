import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { IdCard, ScanLine } from 'lucide-react';
import { Button, SkeletonText } from '@ui/index';
import { EmptyState, ErrorState } from '@components/feedback';
import { PageHeader } from '@components/data';
import { PassCard } from '@components/pass';
import { passApi } from '@lib/api/services/gatepass.api';
import { entryLogApi } from '@lib/api/services/guard.api';
import { passKeys, guardKeys } from '@lib/query/keys';
import { formatDateTime } from '@lib/format/datetime';
import { flags } from '@lib/config';
import { useAuth } from '@hooks/useAuth';
import { PausedBanner } from '../components/PausedBanner';

/**
 * Phase 3 screen 1 — the student's home.
 *
 * ==========================================================================
 * TWO PASSES AT ONCE IS NORMAL, NOT AN ERROR STATE
 * ==========================================================================
 * A student can hold a rolling DAILY pass and an EVENT pass for something
 * starting next week, simultaneously, in different statuses. The dashboard
 * renders every pass it is given rather than picking "the" current one — there
 * is no such thing, and choosing one would hide the other on exactly the day
 * the student needs it.
 *
 * This is also what makes the guard's Behavior 2 attribution necessary: holding
 * two valid QRs, a student scans whichever is on top.
 *
 * ==========================================================================
 * RECENT ACTIVITY IS ENTRIES ONLY
 * ==========================================================================
 * There is no exit row and there never will be. The product scans on entry
 * only — no exit scan, no in/out toggle — so a "last seen leaving" line would
 * be inventing data the gate never collected.
 */
export default function StudentDashboard() {
  const { identity } = useAuth();

  const passes = useQuery({
    queryKey: passKeys.mine(),
    queryFn: () => passApi.mine(),
  });

  /**
   * B9: a STUDENT currently gets 403 on /api/guard/entry-logs/**. The screen is
   * complete and ships dark rather than firing a request that will fail and
   * showing the student an error about a permission they were never meant to
   * think about. Flip VITE_ENABLE_STUDENT_ENTRY_HISTORY when the backend opens
   * the path.
   */
  const entries = useQuery({
    queryKey: guardKeys.entryLogsByHolder(identity?.userId ?? 0, { page: 0, size: 5 }),
    queryFn: () => entryLogApi.byHolder(identity?.userId as number, { page: 0, size: 5 }),
    enabled: flags.studentEntryHistory && identity?.userId != null,
  });

  if (passes.isError) {
    return <ErrorState error={passes.error} onRetry={() => void passes.refetch()} />;
  }

  const all = passes.data ?? [];
  const recent = entries.data?.items ?? [];

  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      {/* Above the greeting, deliberately. See PausedBanner. */}
      <PausedBanner passes={all} />

      <PageHeader
        title={identity?.name ? `Hello, ${identity.name.split(' ')[0]}` : 'Your passes'}
        description="Show a pass at the gate. Entry is scanned on the way in."
      />

      <section aria-labelledby="my-passes">
        <h2 id="my-passes" className="text-h3 mb-[var(--sp-3)] text-[var(--ink-900)]">
          Your passes
        </h2>

        {passes.isPending ? (
          <div className="surface-card p-[var(--sp-6)]"><SkeletonText lines={5} /></div>
        ) : all.length === 0 ? (
          <EmptyState
            icon={IdCard}
            heading="You have no passes yet"
            description="A pass is issued to you by your department or when you register for an event. Nothing to do here until then."
          />
        ) : (
          <div className="grid gap-[var(--sp-4)] sm:grid-cols-2">
            {all.map((pass) => (
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
      </section>

      <section aria-labelledby="recent-activity">
        <div className="mb-[var(--sp-3)] flex items-baseline justify-between gap-[var(--sp-4)]">
          <h2 id="recent-activity" className="text-h3 text-[var(--ink-900)]">
            Recent entries
          </h2>
          {flags.studentEntryHistory && recent.length > 0 && (
            <Button variant="link" asChild>
              <Link to="/student/entries">See all</Link>
            </Button>
          )}
        </div>

        {!flags.studentEntryHistory ? (
          <EmptyState
            icon={ScanLine}
            heading="Entry history is not available yet"
            /* Same explanation as EntryHistoryPage. Two different sentences for
               one blocker (B9) read as two different problems. */
            description="Your gate entries are recorded, but students cannot read the register yet."
          />
        ) : entries.isPending ? (
          <div className="surface-card p-[var(--sp-6)]"><SkeletonText lines={3} /></div>
        ) : recent.length === 0 ? (
          <EmptyState
            icon={ScanLine}
            heading="No entries yet"
            description="Your first scan at a gate will show up here."
          />
        ) : (
          <ul className="surface-card divide-y divide-[var(--border)]">
            {recent.map((entry) => (
              <li
                key={entry.id}
                className="flex items-baseline justify-between gap-[var(--sp-4)] p-[var(--sp-4)]"
              >
                {/* Entered, always. There is no exit row. */}
                <span className="text-body text-[var(--ink-900)]">
                  Entered at {entry.gateName}
                </span>
                <span className="text-small shrink-0 text-[var(--ink-500)]">
                  {formatDateTime(entry.scannedAt)}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
