import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Button, DataTable, StatusBadge, EmptyState, ErrorState,
         TableSkeleton, SearchFilterBar } from '../shared/ui';
import { gatepass, useApi } from '../api';

/**
 * Event management. LIVE.
 *
 * CANCEL, NOT DELETE. Passes already issued against an event have to keep
 * resolving — a guard scanning one needs to be told the event was cancelled,
 * which is a different and more useful answer than "no pass found".
 *
 * `runningToday` is computed backend-side from validFrom/validTo, so this
 * screen never does date arithmetic and can never disagree with the scanner
 * about whether an event is live.
 */
export default function EventList() {
  const [search, setSearch] = useState('');
  const { data, loading, error, reload } = useApi(() => gatepass.events({ size: 100 }), []);

  if (loading) return <TableSkeleton rows={6} cols={5} />;
  if (error)   return <ErrorState title="Could not load events" message={error.message} onRetry={reload} />;

  const all = data?.content ?? data ?? [];
  const rows = all.filter((e) => !search || e.name.toLowerCase().includes(search.toLowerCase()));

  const columns = [
    { key: 'name', header: 'Event', primary: true, sortable: true,
      render: (e) => (
        <div>
          <Link to={`/events/${e.id}`}>{e.name}</Link>
          {e.runningToday && <div className="p-caption">Running today</div>}
        </div>
      ) },
    { key: 'validFrom', header: 'From', sortable: true },
    { key: 'validTo', header: 'To' },
    { key: 'issuedPassCount', header: 'Passes issued', sortable: true },
    { key: 'state', header: 'Status',
      render: (e) => <StatusBadge status={e.cancelled ? 'REVOKED' : (e.runningToday ? 'ACTIVE' : 'PENDING')}
                                  note={e.cancelled ? 'cancelled' : (e.runningToday ? undefined : 'scheduled')} /> },
  ];

  return (
    <div className="p-stack">
      <div className="p-spread">
        <h1 className="p-h1">Events</h1>
        <Link to="/events/new"><Button>Create event</Button></Link>
      </div>

      <div className="p-card">
        <SearchFilterBar search={search} onSearch={setSearch}
                         placeholder="Search events" count={rows.length} countLabel="events" />
        <DataTable columns={columns} rows={rows}
                   empty={<EmptyState icon="⊞" title="No events yet"
                                      message="An event groups passes and gives the scanner something to attribute entries to." />} />
      </div>
    </div>
  );
}
