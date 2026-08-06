import { useMutation, useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router';
import { Calendar, Download, ShieldCheck, Users, ArrowLeft } from 'lucide-react';
import { Badge, Button, SkeletonText } from '@ui/index';
import { EmptyState, ErrorState } from '@components/feedback';
import { eventApi } from '@lib/api/services/gatepass.api';
import { eventKeys } from '@lib/query/keys';
import { saveFile } from '@lib/api/download';
import { formatValidity, formatDate } from '@lib/format/datetime';
import { useToast } from '@hooks/useToast';

export default function EventAttendancePage() {
  const { eventId } = useParams();
  const id = Number(eventId);
  const toast = useToast();

  const summary = useQuery({
    queryKey: eventKeys.attendanceSummary(id),
    queryFn: () => eventApi.attendanceSummary(id),
    enabled: Number.isFinite(id) && id > 0,
  });

  const csv = useMutation({
    mutationFn: () => eventApi.attendeeCsv(id),
    onSuccess: saveFile,
    onError: (error) => toast.fromError(error, 'The attendee list could not be downloaded.'),
  });

  if (summary.isError) {
    return <ErrorState error={summary.error} onRetry={() => void summary.refetch()} />;
  }
  if (summary.isPending || !summary.data) {
    return <div className="rounded-2xl border border-slate-200 bg-white p-8"><SkeletonText lines={5} /></div>;
  }

  const data = summary.data;
  const byStatus = data.registeredByStatus ?? [];

  return (
    <div className="mx-auto max-w-7xl space-y-6 pb-16">
      {/* Back button */}
      <Button variant="ghost" size="sm" asChild className="gap-2 text-slate-600 hover:text-slate-900">
        <Link to="/faculty/events">
          <ArrowLeft className="size-4" /> Back to Events
        </Link>
      </Button>

      {/* Hero Header */}
      <div className="relative overflow-hidden rounded-3xl border border-indigo-100 bg-gradient-to-r from-slate-900 via-indigo-950 to-slate-900 p-8 text-white shadow-xl">
        <div className="flex flex-wrap items-center justify-between gap-6">
          <div className="space-y-2 max-w-2xl">
            <div className="inline-flex items-center gap-2 rounded-full bg-white/10 px-3 py-1 text-xs font-semibold text-indigo-200 backdrop-blur-md border border-white/10">
              <Calendar className="size-3.5 text-indigo-300" /> Event Attendance Roster
            </div>
            <h1 className="text-3xl font-extrabold text-white sm:text-4xl">{data.eventName}</h1>
            <p className="text-sm text-indigo-200/90">
              Validity Window: {formatValidity(data.validFrom, data.validTo)}
            </p>
          </div>

          <Button
            size="lg"
            onClick={() => csv.mutate()}
            loading={csv.isPending}
            className="gap-2 bg-indigo-600 font-semibold text-white shadow-lg shadow-indigo-500/30 hover:bg-indigo-500"
          >
            <Download className="size-4" /> Export Attendee Roster (CSV)
          </Button>
        </div>
      </div>

      {data.cancelled && (
        <div className="rounded-2xl border border-rose-200 bg-rose-50 p-6 text-rose-900 space-y-1">
          <p className="font-bold text-base">This event was cancelled</p>
          <p className="text-xs text-rose-700">
            Every pass issued for this event has been revoked. Historical entry logs are preserved for audit reporting.
          </p>
        </div>
      )}

      {/* Metrics Cards */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <div className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm flex items-center justify-between">
          <div>
            <dt className="text-xs font-semibold text-slate-500">Total Passes Issued</dt>
            <dd className="mt-1 text-2xl font-extrabold text-slate-900">{data.totalPasses}</dd>
          </div>
          <div className="flex size-10 items-center justify-center rounded-xl bg-indigo-50 text-indigo-700 border border-indigo-100">
            <ShieldCheck className="size-5" />
          </div>
        </div>

        <div className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm flex items-center justify-between">
          <div>
            <dt className="text-xs font-semibold text-slate-500">Registered Attendees</dt>
            <dd className="mt-1 text-2xl font-extrabold text-slate-900">{data.registeredCount}</dd>
          </div>
          <div className="flex size-10 items-center justify-center rounded-xl bg-emerald-50 text-emerald-700 border border-emerald-100">
            <Users className="size-5" />
          </div>
        </div>

        <div className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm flex items-center justify-between">
          <div>
            <dt className="text-xs font-semibold text-slate-500">Event Duration</dt>
            <dd className="mt-1 text-2xl font-extrabold text-slate-900">
              {data.eventDays.length} {data.eventDays.length === 1 ? 'Day' : 'Days'}
            </dd>
          </div>
          <div className="flex size-10 items-center justify-center rounded-xl bg-slate-50 text-slate-700 border border-slate-200">
            <Calendar className="size-5" />
          </div>
        </div>

        <div className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm flex flex-col justify-center">
          <dt className="text-xs font-semibold text-slate-500">Event Dates</dt>
          <dd className="mt-1 text-xs font-bold text-slate-800 truncate">
            {data.eventDays.map((day) => formatDate(day)).join(', ')}
          </dd>
        </div>
      </div>

      {/* Breakdown by status */}
      <section className="space-y-4">
        <h2 className="font-bold text-slate-900 text-lg">Pass Status Breakdown</h2>

        {byStatus.length === 0 ? (
          <EmptyState
            icon={Download}
            heading="No passes issued yet"
            description="Upload an attendee sheet against this event to issue passes."
          />
        ) : (
          <div className="overflow-hidden rounded-2xl border border-slate-200/80 bg-white shadow-sm divide-y divide-slate-100">
            {byStatus.map((row) => (
              <div key={row.status} className="flex items-center justify-between p-4 px-6 hover:bg-slate-50/80 transition-colors">
                <Badge tone="neutral">{row.status.charAt(0) + row.status.slice(1).toLowerCase()}</Badge>
                <span className="font-mono font-bold text-sm text-slate-900">{row.count} Passes</span>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
