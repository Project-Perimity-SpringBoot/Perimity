import { useParams } from 'react-router-dom';
import { StatCard, StatRow, DataTable, EmptyState, ErrorState,
         DetailSkeleton, Button } from '../shared/ui';
import { gatepass, eventAttendanceFor, useApi } from '../api';

/**
 * The organiser's attendance view. LIVE against guard-service.
 *
 * REGISTERED / ATTENDED / NEVER SHOWED, then a row per day.
 *
 * "Never showed" is the number organisers actually want and nobody builds. It
 * is derived server-side as registered − uniqueAttendees, which is the only
 * way it can be right: counting it in the browser from a paged entry list
 * would double-count anyone who came on two days.
 *
 * Note what per-day attendance is NOT: it is not a sum. Someone who came on
 * three days appears in three day-rows and once in the unique total, so the
 * day figures deliberately do not add up to the total, and the caption says so
 * rather than leaving somebody to discover it in a viva.
 */
export default function AttendanceView() {
  const { id } = useParams();
  const { data, loading, error, reload } = useApi(() => eventAttendanceFor(id), [id]);

  if (loading) return <DetailSkeleton />;
  if (error)   return <ErrorState title="Could not load attendance"
                                  message={error.message} onRetry={reload} />;

  const columns = [
    { key: 'scanDate', header: 'Day', primary: true, sortable: true },
    { key: 'attendedCount', header: 'Attended', sortable: true },
    { key: 'attendancePercent', header: 'Of registered',
      render: (d) => `${d.attendancePercent}%` },
  ];

  return (
    <div className="p-stack">
      <div className="p-spread">
        <div>
          <h1 className="p-h1">{data.eventName}</h1>
          <p className="p-caption">Attendance</p>
        </div>
        {/* A direct link, not a fetch — the browser downloads it with the
            Authorization header it already has for this origin. */}
        <a href={gatepass.attendeesCsvUrl(id)} download>
          <Button variant="secondary">Export CSV</Button>
        </a>
      </div>

      <StatRow>
        <StatCard value={data.registeredCount} label="Registered"
                  hint="Issued a pass for this event." />
        <StatCard value={data.uniqueAttendeeCount} label="Turned up"
                  hint="Distinct people, however many days they came." />
        <StatCard value={data.neverShowedCount} label="Never showed" />
      </StatRow>

      <div className="p-card">
        <div className="p-pad-sm"><span className="p-label">By day</span></div>
        <DataTable columns={columns} rows={data.days ?? []}
                   empty={<EmptyState icon="⇢" title="Nobody has scanned in yet"
                                      message="Days appear here as people arrive." />} />
      </div>

      <p className="p-caption">
        Day figures do not add up to "turned up". Somebody who came on three
        days is counted in three day-rows and once in the total.
      </p>
    </div>
  );
}
