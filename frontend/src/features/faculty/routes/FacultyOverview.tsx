import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import {
  CalendarRange,
  ClipboardCheck,
  UserCheck,
  Users,
  Sparkles,
  ArrowRight,
  Plus,
  FileSpreadsheet,
  Calendar,
  Clock,
  ChevronRight
} from 'lucide-react';
import { Button, SkeletonText } from '@ui/index';
import { EmptyState, ErrorState } from '@components/feedback';
import { ApprovalDrawer } from '@components/approval';
import { visitorRequestApi, eventApi } from '@lib/api/services/gatepass.api';
import { requestKeys, eventKeys } from '@lib/query/keys';
import { formatDateTime, formatValidity } from '@lib/format/datetime';
import { useAuth } from '@hooks/useAuth';
import type { VisitorRequestResponse } from '@/types/gatepass.types';
import { ActiveBatchesPanel } from '../components/ActiveBatchesPanel';

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
    <div className="mx-auto max-w-7xl space-y-8 pb-16">
      {/* Hero Welcome Header */}
      <div className="relative overflow-hidden rounded-3xl border border-indigo-100 bg-gradient-to-r from-slate-900 via-indigo-950 to-slate-900 p-8 text-white shadow-xl">
        <div className="relative z-10 flex flex-wrap items-center justify-between gap-6">
          <div className="max-w-2xl space-y-3">
            <div className="inline-flex items-center gap-2 rounded-full bg-white/10 px-3.5 py-1 text-xs font-semibold text-indigo-200 backdrop-blur-md border border-white/10">
              <Sparkles className="size-3.5 text-indigo-300" />
              <span>Faculty Dashboard & Action Queue</span>
            </div>
            <h1 className="text-3xl font-extrabold tracking-tight text-white sm:text-4xl">
              Welcome back, Dr. {nameStr}
            </h1>
            <p className="text-sm leading-relaxed text-indigo-200/90">
              Manage visitor approvals naming you as host, onboard student cohorts, and monitor live campus events.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <Button asChild size="sm" className="bg-indigo-600 font-semibold text-white shadow-lg shadow-indigo-500/30 hover:bg-indigo-500 gap-2">
              <Link to="/faculty/students/import">
                <FileSpreadsheet className="size-4" /> Import Student Cohort
              </Link>
            </Button>
            <Button asChild variant="secondary" size="sm" className="bg-white/10 text-white hover:bg-white/20 border-white/10 backdrop-blur-md gap-2">
              <Link to="/faculty/students/add">
                <Plus className="size-4" /> Add Single Student
              </Link>
            </Button>
          </div>
        </div>
      </div>

      {/* Pending Approvals Section */}
      <section aria-labelledby="pending-approvals" className="space-y-4">
        <div className="flex items-center justify-between border-b border-slate-200/80 pb-4">
          <div className="flex items-center gap-3">
            <div className="flex size-10 items-center justify-center rounded-xl bg-amber-50 text-amber-600 border border-amber-200/60">
              <ClipboardCheck className="size-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 id="pending-approvals" className="font-bold text-slate-900 text-lg">
                  Waiting for Your Review
                </h2>
                {waiting > 0 && (
                  <span className="rounded-full bg-amber-100 px-2.5 py-0.5 font-bold text-xs text-amber-800">
                    {waiting} Pending
                  </span>
                )}
              </div>
              <p className="text-xs text-slate-500">Visitor requests naming you as host. Click to approve or reject.</p>
            </div>
          </div>

          {waiting > rows.length && (
            <Button variant="ghost" size="sm" asChild className="gap-1 text-indigo-600 hover:text-indigo-700 font-semibold">
              <Link to="/faculty/approvals">
                See all {waiting} requests <ArrowRight className="size-4" />
              </Link>
            </Button>
          )}
        </div>

        {pending.isPending ? (
          <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
            <SkeletonText lines={4} />
          </div>
        ) : rows.length === 0 ? (
          <EmptyState
            icon={ClipboardCheck}
            heading="Nothing waiting for you"
            description="Visitor requests naming you as host will appear here. You will receive an email notification when one arrives."
          />
        ) : (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {rows.map((request) => (
              <button
                key={request.id}
                type="button"
                onClick={() => setSelected(request)}
                className="group relative flex flex-col justify-between rounded-2xl border border-slate-200/80 bg-white p-5 text-left shadow-sm transition-all hover:border-indigo-300 hover:shadow-md hover:shadow-indigo-500/5 focus-visible:outline-2 focus-visible:outline-indigo-600"
              >
                <div className="space-y-3">
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex items-center gap-3">
                      <div className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-indigo-50 font-bold text-sm text-indigo-700 border border-indigo-100">
                        {request.visitorName?.charAt(0) ?? 'V'}
                      </div>
                      <div className="min-w-0">
                        <h3 className="font-bold text-slate-900 text-sm truncate group-hover:text-indigo-600 transition-colors">
                          {request.visitorName}
                        </h3>
                        <p className="text-xs truncate text-slate-500">{request.visitorEmail}</p>
                      </div>
                    </div>
                  </div>

                  <p className="text-xs text-slate-600 line-clamp-2 bg-slate-50/80 rounded-lg p-2.5 border border-slate-100">
                    "{request.purpose}"
                  </p>
                </div>

                <div className="mt-4 flex items-center justify-between border-t border-slate-100 pt-3 text-[11px] text-slate-500">
                  <span className="flex items-center gap-1">
                    <Clock className="size-3 text-slate-400" />
                    {formatDateTime(request.createdAt)}
                  </span>
                  <span className="flex items-center font-semibold text-indigo-600 group-hover:translate-x-0.5 transition-transform">
                    Review Request <ChevronRight className="size-3.5" />
                  </span>
                </div>
              </button>
            ))}
          </div>
        )}
      </section>

      {/* Active Batches Panel */}
      <ActiveBatchesPanel />

      {/* Live Events Today Section */}
      <section aria-labelledby="live-events" className="space-y-4">
        <div className="flex items-center justify-between border-b border-slate-200/80 pb-4">
          <div className="flex items-center gap-3">
            <div className="flex size-10 items-center justify-center rounded-xl bg-emerald-50 text-emerald-600 border border-emerald-200/60">
              <CalendarRange className="size-5" />
            </div>
            <div>
              <h2 id="live-events" className="font-bold text-slate-900 text-lg">
                Events Running Today
              </h2>
              <p className="text-xs text-slate-500">Active campus events and real-time registration status.</p>
            </div>
          </div>

          <Button variant="ghost" size="sm" asChild className="gap-1 text-indigo-600 hover:text-indigo-700 font-semibold">
            <Link to="/faculty/events">
              All events <ArrowRight className="size-4" />
            </Link>
          </Button>
        </div>

        {events.isPending ? (
          <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
            <SkeletonText lines={2} />
          </div>
        ) : running.length === 0 ? (
          <EmptyState
            icon={CalendarRange}
            heading="No events running today"
            description="Events you create will appear here on the days they run."
          />
        ) : (
          <div className="grid gap-4 sm:grid-cols-2">
            {running.map((event) => (
              <Link
                key={event.id}
                to={`/faculty/events/${event.id}/attendance`}
                className="group relative flex items-center justify-between rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm transition-all hover:border-emerald-300 hover:shadow-md"
              >
                <div className="flex items-center gap-4 min-w-0">
                  <div className="flex size-12 shrink-0 items-center justify-center rounded-xl bg-emerald-50 text-emerald-700 font-bold border border-emerald-200">
                    <Calendar className="size-6" />
                  </div>
                  <div className="min-w-0">
                    <h3 className="font-bold text-slate-900 text-base truncate group-hover:text-emerald-700 transition-colors">
                      {event.name}
                    </h3>
                    <p className="text-xs text-slate-500 mt-0.5">
                      {formatValidity(event.validFrom, event.validTo)}
                    </p>
                  </div>
                </div>

                <div className="text-right shrink-0">
                  <span className="inline-flex items-center rounded-full bg-emerald-100 px-3 py-1 font-mono text-xs font-bold text-emerald-800">
                    {event.issuedPassCount} Registered
                  </span>
                </div>
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
