import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { CalendarRange, ClipboardCheck } from 'lucide-react';
import { Button, SkeletonText } from '@ui/index';
import { EmptyState, ErrorState } from '@components/feedback';
import { PageHeader } from '@components/data';
import { ApprovalDrawer } from '@components/approval';
import { visitorRequestApi, eventApi } from '@lib/api/services/gatepass.api';
import { requestKeys, eventKeys } from '@lib/query/keys';
import { formatDateTime, formatValidity } from '@lib/format/datetime';
import { useAuth } from '@hooks/useAuth';
import type { VisitorRequestResponse } from '@/types/gatepass.types';
import { ActiveBatchesPanel } from '../components/ActiveBatchesPanel';

/**
 * Phase 4 screen 1 — the faculty member's home.
 *
 * ==========================================================================
 * A WORK QUEUE, NOT A STATISTICS PAGE
 * ==========================================================================
 * A Campus Admin's dashboard answers "how is the campus doing". A faculty
 * member's answers "what is waiting for me". So the pending approvals come
 * first and at full width, and there is no stat row competing with them: a
 * number telling somebody they have 7 things to do, above a list of the 7
 * things, is a row of pixels that costs a scroll and returns nothing.
 *
 * ==========================================================================
 * ROWS OPEN THE DRAWER DIRECTLY
 * ==========================================================================
 * Clicking a preview row opens the approval drawer here, on this screen. It
 * does NOT navigate to /faculty/approvals and make the user find the row again.
 * The whole point of a preview is to decide without leaving.
 *
 * The drawer itself is @components/approval — the same component the Campus
 * Admin queue uses, against the same endpoint with the same server-side rules.
 * Two copies would drift, and the check that would drift is a blocklist match.
 */
export default function FacultyOverview() {
  const { identity } = useAuth();
  const [selected, setSelected] = useState<VisitorRequestResponse | null>(null);

  /**
   * `/mine` rather than the campus queue: a faculty member hosts requests that
   * name them. The campus-wide view is the Campus Admin's screen, and showing
   * it here would put another department's visitors in this person's queue.
   *
   * No `sort` parameter — Spring Data emits two ORDER BY clauses and the query
   * fails outright. The server already returns a queue oldest-first.
   */
  const pending = useQuery({
    queryKey: requestKeys.myQueue('PENDING', { page: 0, size: 3 }),
    queryFn: () => visitorRequestApi.myQueue('PENDING', { page: 0, size: 3 }),
  });

  const events = useQuery({
    queryKey: eventKeys.running(),
    queryFn: () => eventApi.runningToday(),
  });

  if (pending.isError) {
    return <ErrorState error={pending.error} onRetry={() => void pending.refetch()} />;
  }

  const rows = pending.data?.items ?? [];
  const waiting = pending.data?.total ?? 0;
  const running = events.data ?? [];
  const firstName = identity?.name?.split(' ').slice(-1)[0];

  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      <PageHeader
        title={firstName ? `Welcome back, Dr. ${firstName}` : 'Your queue'}
        description="Requests naming you as host, and the batches you have running."
      />

      <section aria-labelledby="pending-approvals">
        <div className="mb-[var(--sp-3)] flex items-baseline justify-between gap-[var(--sp-4)]">
          <h2 id="pending-approvals" className="text-h3 text-[var(--ink-900)]">
            Waiting for you
            {waiting > 0 ? (
              <span className="text-body ml-[var(--sp-2)] font-normal text-[var(--ink-500)]">
                {waiting}
              </span>
            ) : null}
          </h2>
          {waiting > rows.length ? (
            <Button variant="link" asChild>
              <Link to="/faculty/approvals">See all</Link>
            </Button>
          ) : null}
        </div>

        {pending.isPending ? (
          <div className="surface-card p-[var(--sp-6)]"><SkeletonText lines={4} /></div>
        ) : rows.length === 0 ? (
          <EmptyState
            icon={ClipboardCheck}
            heading="Nothing waiting"
            description="Visitor requests naming you as host will appear here. You are emailed when one arrives."
          />
        ) : (
          <ul className="surface-card divide-y divide-[var(--border)]">
            {rows.map((request) => (
              <li key={request.id}>
                {/* Opens the drawer here. No navigation — see the file comment. */}
                <button
                  type="button"
                  onClick={() => setSelected(request)}
                  className="flex w-full flex-col gap-[var(--sp-1)] p-[var(--sp-4)] text-left
                             transition-colors hover:bg-[var(--surface-sunken)]
                             focus-visible:outline focus-visible:outline-2
                             focus-visible:-outline-offset-2 focus-visible:outline-[var(--brand-600)]"
                >
                  <div className="flex items-baseline justify-between gap-[var(--sp-4)]">
                    <span className="text-body-md truncate text-[var(--ink-900)]">
                      {request.visitorName}
                    </span>
                    <span className="text-caption shrink-0 text-[var(--ink-500)]">
                      {formatDateTime(request.createdAt)}
                    </span>
                  </div>
                  <span className="text-caption line-clamp-1 text-[var(--ink-500)]">
                    {request.purpose}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      {/* Renders nothing when no batch is unfinished. See ActiveBatchesPanel. */}
      <ActiveBatchesPanel />

      <section aria-labelledby="live-events">
        <div className="mb-[var(--sp-3)] flex items-baseline justify-between gap-[var(--sp-4)]">
          <h2 id="live-events" className="text-h3 text-[var(--ink-900)]">
            Running today
          </h2>
          <Button variant="link" asChild>
            <Link to="/faculty/events">All events</Link>
          </Button>
        </div>

        {events.isPending ? (
          <div className="surface-card p-[var(--sp-6)]"><SkeletonText lines={2} /></div>
        ) : running.length === 0 ? (
          <EmptyState
            icon={CalendarRange}
            heading="No events running today"
            description="Events you create appear here on the days they run."
          />
        ) : (
          <ul className="surface-card divide-y divide-[var(--border)]">
            {running.map((event) => (
              <li key={event.id}>
                <Link
                  to={`/faculty/events/${event.id}/attendance`}
                  className="flex items-baseline justify-between gap-[var(--sp-4)] p-[var(--sp-4)]
                             transition-colors hover:bg-[var(--surface-sunken)]
                             focus-visible:outline focus-visible:outline-2
                             focus-visible:-outline-offset-2 focus-visible:outline-[var(--brand-600)]"
                >
                  <div className="min-w-0">
                    <p className="text-body-md truncate text-[var(--ink-900)]">{event.name}</p>
                    <p className="text-caption text-[var(--ink-500)]">
                      {formatValidity(event.validFrom, event.validTo)}
                    </p>
                  </div>
                  <span className="text-caption shrink-0 text-[var(--ink-500)]">
                    {event.issuedPassCount} registered
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      <ApprovalDrawer
        request={selected}
        open={selected !== null}
        onOpenChange={(open) => { if (!open) setSelected(null); }}
      />
    </div>
  );
}
