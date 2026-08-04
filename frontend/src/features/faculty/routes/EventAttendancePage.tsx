import { useMutation, useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router';
import { Download } from 'lucide-react';
import { Badge, Button, SkeletonText } from '@ui/index';
import { PageHeader, DescriptionList } from '@components/data';
import { EmptyState, ErrorState } from '@components/feedback';
import { eventApi } from '@lib/api/services/gatepass.api';
import { eventKeys } from '@lib/query/keys';
import { saveFile } from '@lib/api/download';
import { formatValidity, formatDate } from '@lib/format/datetime';
import { useToast } from '@hooks/useToast';

/**
 * Phase 4 screen 10 — attendance for one event.
 *
 * ==========================================================================
 * REGISTERED IS NOT ATTENDED
 * ==========================================================================
 * This screen reports what gatepass-service knows: how many passes were issued
 * for the event and what state they are in. Who actually walked through a gate
 * is an entry log in guard-service, attributed by the scanner's Behavior 2, and
 * it is a different question with a different owner.
 *
 * The two are labelled apart rather than blended into one "attendance" number,
 * because an organiser reading "580" needs to know whether that is 580 people
 * who registered or 580 who turned up. Blending them would make the more
 * flattering reading the default.
 *
 * Statuses are neutral badges told apart by their word — green and red are the
 * guard verdict screens' alone.
 */
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
    return <div className="surface-card p-[var(--sp-6)]"><SkeletonText lines={5} /></div>;
  }

  const data = summary.data;
  const byStatus = data.registeredByStatus ?? [];

  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      <PageHeader
        title={data.eventName}
        description={formatValidity(data.validFrom, data.validTo)}
        actions={
          <div className="flex flex-wrap gap-[var(--sp-3)]">
            <Button variant="secondary" asChild>
              <Link to="/faculty/events">All events</Link>
            </Button>
            <Button onClick={() => csv.mutate()} loading={csv.isPending}>
              <Download aria-hidden />Attendee list
            </Button>
          </div>
        }
      />

      {data.cancelled ? (
        <div className="surface-card p-[var(--sp-5)]">
          <p className="text-body-md text-[var(--ink-900)]">This event was cancelled.</p>
          <p className="text-caption text-[var(--ink-500)]">
            Every pass issued for it was revoked. The record is kept because the entry
            logs recorded against it are still the attendance history.
          </p>
        </div>
      ) : null}

      <div className="surface-card p-[var(--sp-6)]">
        <DescriptionList
          columns={2}
          items={[
            { label: 'Passes issued', value: String(data.totalPasses) },
            { label: 'Registered attendees', value: String(data.registeredCount) },
            { label: 'Runs for', value: `${data.eventDays.length} ${data.eventDays.length === 1 ? 'day' : 'days'}` },
            {
              label: 'Days',
              value: data.eventDays.map((day) => formatDate(day)).join(', '),
            },
          ]}
        />
      </div>

      <section aria-labelledby="by-status">
        <h2 id="by-status" className="text-h3 mb-[var(--sp-3)] text-[var(--ink-900)]">
          Passes by status
        </h2>

        {byStatus.length === 0 ? (
          <EmptyState
            icon={Download}
            heading="No passes issued yet"
            description="Upload an attendee sheet against this event to create them."
          />
        ) : (
          <ul className="surface-card divide-y divide-[var(--border)]">
            {byStatus.map((row) => (
              <li
                key={row.status}
                className="flex items-center justify-between gap-[var(--sp-4)] p-[var(--sp-4)]"
              >
                {/* Neutral badge. The word carries the meaning, not a colour. */}
                <Badge>{row.status.charAt(0) + row.status.slice(1).toLowerCase()}</Badge>
                <span className="text-body-md text-[var(--ink-900)]">{row.count}</span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
