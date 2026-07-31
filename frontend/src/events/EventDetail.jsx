import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, StatCard, StatRow, StatusBadge, DataTable, Modal,
         ErrorState, DetailSkeleton, EmptyState } from '../shared/ui';
import { gatepass, eventAttendanceFor, useApi } from '../api';

/**
 * One event, tying its passes and its attendance together. LIVE.
 *
 * Three numbers that answer the organiser's actual question: how many were
 * issued a pass, how many turned up, and who never did. The third is the one
 * nobody builds and everybody asks for.
 */
export default function EventDetail() {
  const { id } = useParams();
  const [cancelling, setCancelling] = useState(false);

  const { data: event, loading, error, reload } = useApi(() => gatepass.event(id), [id]);
  /*
   * Attendance needs both services joined — see api/attendance.js. Calling
   * gatepass's attendance-summary alone gives registeredCount and nothing
   * about who turned up, which is how "never showed" quietly becomes 0.
   */
  const { data: attendance } = useApi(() => eventAttendanceFor(id), [id]);
  const { data: passes } = useApi(() => gatepass.passesForEvent(id, { size: 50 }), [id]);

  if (loading) return <DetailSkeleton />;
  if (error)   return <ErrorState title="Could not load this event" message={error.message} onRetry={reload} />;

  const rows = passes?.content ?? passes ?? [];
  const columns = [
    { key: 'holderName', header: 'Holder', primary: true, sortable: true },
    { key: 'id', header: 'Pass', render: (p) => <span className="p-mono">#{p.id}</span> },
    { key: 'validFrom', header: 'Valid from' },
    { key: 'status', header: 'Status', render: (p) => <StatusBadge status={p.status} /> },
  ];

  return (
    <div className="p-stack">
      <div className="p-spread">
        <div>
          <h1 className="p-h1">{event.name}</h1>
          <p className="p-caption">
            {event.validFrom} – {event.validTo}
            {event.runningToday && ' · running today'}
          </p>
        </div>
        <div className="p-row">
          <Link to={`/events/${id}/attendance`}><Button variant="secondary">Attendance</Button></Link>
          {!event.cancelled && (
            <>
              <Link to={`/events/${id}/edit`}><Button variant="secondary">Edit</Button></Link>
              <Button variant="danger" onClick={() => setCancelling(true)}>Cancel event</Button>
            </>
          )}
        </div>
      </div>

      {event.cancelled && (
        <div className="p-card p-pad-sm">
          <span className="p-label">Cancelled</span>
          <p className="p-caption" style={{ marginBottom: 0 }}>
            Passes for this event still resolve at the gate — a guard scanning
            one is told the event was cancelled, which is more useful than
            "pass not found".
          </p>
        </div>
      )}

      <StatRow>
        <StatCard value={event.issuedPassCount} label="Passes issued" />
        <StatCard value={attendance?.uniqueAttendeeCount ?? '—'} label="Turned up" />
        <StatCard value={attendance?.neverShowedCount ?? '—'} label="Never showed"
                  hint="Issued a pass and never scanned in." />
      </StatRow>

      <div className="p-card">
        <div className="p-pad-sm"><span className="p-label">Passes</span></div>
        <DataTable columns={columns} rows={rows}
                   empty={<EmptyState title="No passes yet"
                                      message="Issue them by bulk upload, or one at a time." />} />
      </div>

      <Modal open={cancelling} onClose={() => setCancelling(false)} title={`Cancel ${event.name}?`}
             footer={<>
               <Button variant="secondary" onClick={() => setCancelling(false)}>Keep event</Button>
               <Button variant="danger"
                       onClick={async () => { await gatepass.cancelEvent(id); setCancelling(false); reload(); }}>
                 Cancel event
               </Button>
             </>}>
        <p>
          {event.issuedPassCount} passes have been issued. They are not deleted —
          each will be refused at the gate with the reason "event cancelled".
        </p>
      </Modal>
    </div>
  );
}
