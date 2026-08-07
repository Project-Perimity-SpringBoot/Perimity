import { useMutation, useQuery } from '@tanstack/react-query';
import { useParams } from 'react-router';
import { Calendar, Download, ShieldCheck, Users } from 'lucide-react';
import { Badge, Button, SkeletonText } from '@ui/index';
import { PageHeader, SectionHeader, StatCard } from '@components/data';
import { Alert, EmptyState, ErrorState } from '@components/feedback';
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
    return (
      <div className="surface-panel p-[var(--sp-6)]">
        <SkeletonText lines={5} />
      </div>
    );
  }

  const data = summary.data;
  const byStatus = data.registeredByStatus ?? [];

  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      <PageHeader
        breadcrumbs={[
          { label: 'Faculty', to: '/faculty' },
          { label: 'Events', to: '/faculty/events' },
          { label: data.eventName },
        ]}
        title={data.eventName}
        description={`Valid ${formatValidity(data.validFrom, data.validTo)}`}
        actions={
          <Button onClick={() => csv.mutate()} loading={csv.isPending}>
            <Download aria-hidden /> Export roster
          </Button>
        }
      />

      {data.cancelled && (
        <Alert tone="danger" title="This event was cancelled">
          Every pass issued for it has been revoked. Historical entry logs are kept for audit.
        </Alert>
      )}

      <div className="grid gap-[var(--sp-4)] sm:grid-cols-2 lg:grid-cols-3">
        <StatCard label="Passes issued" value={data.totalPasses} icon={ShieldCheck} />
        <StatCard label="Registered attendees" value={data.registeredCount} icon={Users} />
        <StatCard
          label="Runs for"
          value={`${data.eventDays.length} ${data.eventDays.length === 1 ? 'day' : 'days'}`}
          icon={Calendar}
          hint={data.eventDays.map((day) => formatDate(day)).join(', ')}
        />
      </div>

      <section className="flex flex-col gap-[var(--sp-4)]">
        <SectionHeader
          title="Pass status breakdown"
          description="Where the issued passes currently stand."
          divided
        />

        {byStatus.length === 0 ? (
          <EmptyState
            icon={Download}
            heading="No passes issued yet"
            description="Upload an attendee sheet against this event to issue passes."
          />
        ) : (
          <ul className="surface-panel overflow-hidden">
            {byStatus.map((row) => (
              <li
                key={row.status}
                className="flex items-center justify-between border-b border-[var(--border)] px-[var(--sp-4)] py-[var(--sp-4)] last:border-0"
              >
                <Badge>{row.status.charAt(0) + row.status.slice(1).toLowerCase()}</Badge>
                <span className="text-body-md tabular-nums text-[var(--ink-900)]">
                  {row.count} {row.count === 1 ? 'pass' : 'passes'}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
