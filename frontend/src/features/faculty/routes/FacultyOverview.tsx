import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import {
  ArrowRight,
  Calendar,
  CalendarRange,
  ChevronRight,
  ClipboardCheck,
  Clock,
  FileSpreadsheet,
  Plus,
} from 'lucide-react';
import { Badge, Button, SkeletonText } from '@ui/index';
import { PageHeader, SectionHeader } from '@components/data';
import { EmptyState, ErrorState } from '@components/feedback';
import { ApprovalDrawer } from '@components/approval';
import { visitorRequestApi, eventApi } from '@lib/api/services/gatepass.api';
import { requestKeys, eventKeys } from '@lib/query/keys';
import { formatDateTime, formatValidity } from '@lib/format/datetime';
import { useAuth } from '@hooks/useAuth';
import type { VisitorRequestResponse } from '@/types/gatepass.types';
import { ActiveBatchesPanel } from '../components/ActiveBatchesPanel';
import { StudentStats } from '../components/StudentStats';
import { RecentImportsPanel } from '../components/RecentImportsPanel';

export default function FacultyOverview() {
  const { identity } = useAuth();
  const [selected, setSelected] = useState<VisitorRequestResponse | null>(null);

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
  const nameStr = identity?.name || 'Faculty Member';

  return (
    <div className="flex flex-col gap-[var(--sp-8)]">
      <PageHeader
        title={`Welcome back, ${nameStr}`}
        description="Approve visitor requests naming you as host, onboard student cohorts, and keep an eye on today's events."
        actions={
          <>
            <Button asChild>
              <Link to="/faculty/students/import">
                <FileSpreadsheet aria-hidden /> Import cohort
              </Link>
            </Button>
            <Button asChild variant="secondary">
              <Link to="/faculty/students/add">
                <Plus aria-hidden /> Add student
              </Link>
            </Button>
          </>
        }
      />

      {/* The cohort at a glance, directly under the welcome header */}
      <StudentStats />

      <section aria-labelledby="pending-approvals" className="flex flex-col gap-[var(--sp-4)]">
        <SectionHeader
          id="pending-approvals"
          icon={ClipboardCheck}
          title="Waiting for your review"
          description="Visitor requests naming you as host. Open one to approve or reject it."
          divided
          {...(waiting > 0 ? { badge: <Badge tone="brand">{waiting} pending</Badge> } : {})}
          {...(waiting > rows.length
            ? {
                actions: (
                  <Button variant="ghost" size="sm" asChild>
                    <Link to="/faculty/approvals">
                      See all {waiting} <ArrowRight aria-hidden />
                    </Link>
                  </Button>
                ),
              }
            : {})}
        />

        {pending.isPending ? (
          <div className="surface-card p-[var(--sp-6)]">
            <SkeletonText lines={4} />
          </div>
        ) : rows.length === 0 ? (
          <EmptyState
            icon={ClipboardCheck}
            heading="Nothing waiting for you"
            description="Visitor requests naming you as host appear here. You are emailed when one arrives."
          />
        ) : (
          <div className="grid gap-[var(--sp-4)] sm:grid-cols-2 lg:grid-cols-3">
            {rows.map((request) => (
              <button
                key={request.id}
                type="button"
                onClick={() => setSelected(request)}
                className="surface-card lift group flex flex-col justify-between gap-[var(--sp-4)] p-[var(--sp-4)] text-left"
              >
                <div className="flex flex-col gap-[var(--sp-3)]">
                  <div className="flex items-center gap-[var(--sp-3)]">
                    <span className="text-body-md flex size-10 shrink-0 items-center justify-center rounded-[var(--r-md)] bg-[var(--brand-50)] text-[var(--brand-600)]">
                      {request.visitorName?.charAt(0) ?? 'V'}
                    </span>
                    <span className="min-w-0">
                      <span className="text-body-md block truncate text-[var(--ink-900)]">
                        {request.visitorName}
                      </span>
                      <span className="text-caption block truncate text-[var(--ink-500)]">
                        {request.visitorEmail}
                      </span>
                    </span>
                  </div>

                  <p className="text-small surface-inset line-clamp-2 p-[var(--sp-3)] text-[var(--ink-700)]">
                    {request.purpose}
                  </p>
                </div>

                <div className="text-caption flex items-center justify-between border-t border-[var(--border)] pt-[var(--sp-3)] text-[var(--ink-500)]">
                  <span className="flex items-center gap-[var(--sp-1)]">
                    <Clock className="size-3" aria-hidden />
                    {formatDateTime(request.createdAt)}
                  </span>
                  <span className="flex items-center gap-[var(--sp-1)] text-[var(--brand-600)]">
                    Review
                    <ChevronRight
                      className="size-3 transition-transform duration-[var(--motion-fast)] group-hover:translate-x-0.5"
                      aria-hidden
                    />
                  </span>
                </div>
              </button>
            ))}
          </div>
        )}
      </section>

      {/* Student cohort: the numbers above, the imports behind them here */}
      <RecentImportsPanel />

      {/*
        Visitor bulk uploads, NOT student imports. The two are unrelated
        features that both use the word "batch", so the student panel sits
        above this one and names itself explicitly.
      */}
      <ActiveBatchesPanel />

      <section aria-labelledby="live-events" className="flex flex-col gap-[var(--sp-4)]">
        <SectionHeader
          id="live-events"
          icon={CalendarRange}
          title="Events running today"
          description="Active campus events and how many attendees are registered."
          divided
          actions={
            <Button variant="ghost" size="sm" asChild>
              <Link to="/faculty/events">
                All events <ArrowRight aria-hidden />
              </Link>
            </Button>
          }
        />

        {events.isPending ? (
          <div className="surface-card p-[var(--sp-6)]">
            <SkeletonText lines={2} />
          </div>
        ) : running.length === 0 ? (
          <EmptyState
            icon={CalendarRange}
            heading="No events running today"
            description="Events you create appear here on the days they run."
          />
        ) : (
          <div className="grid gap-[var(--sp-4)] sm:grid-cols-2">
            {running.map((event) => (
              <Link
                key={event.id}
                to={`/faculty/events/${event.id}/attendance`}
                className="surface-card lift flex items-center justify-between gap-[var(--sp-4)] p-[var(--sp-4)]"
              >
                <div className="flex min-w-0 items-center gap-[var(--sp-3)]">
                  <span className="flex size-10 shrink-0 items-center justify-center rounded-[var(--r-md)] bg-[var(--brand-50)]">
                    <Calendar className="size-5 text-[var(--brand-600)]" aria-hidden />
                  </span>
                  <span className="min-w-0">
                    <span className="text-body-md block truncate text-[var(--ink-900)]">
                      {event.name}
                    </span>
                    <span className="text-caption block text-[var(--ink-500)]">
                      {formatValidity(event.validFrom, event.validTo)}
                    </span>
                  </span>
                </div>

                <Badge className="shrink-0">{event.issuedPassCount} registered</Badge>
              </Link>
            ))}
          </div>
        )}
      </section>

      <ApprovalDrawer
        request={selected}
        open={selected !== null}
        onOpenChange={(open) => {
          if (!open) setSelected(null);
        }}
      />
    </div>
  );
}
