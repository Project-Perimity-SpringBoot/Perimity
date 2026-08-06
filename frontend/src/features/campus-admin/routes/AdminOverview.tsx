import { useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import dayjs from 'dayjs';
import {
  AlertTriangle, ClipboardList, DoorOpen, IdCard, Radio, ScanLine,
} from 'lucide-react';
import { Badge, Button, Skeleton } from '@ui/index';
import { PageHeader, Pagination, StatCard } from '@components/data';
import { EmptyState, ErrorState } from '@components/feedback';
import { passApi } from '@lib/api/services/gatepass.api';
import { gateApi } from '@lib/api/services/campus.api';
import { entryLogApi, sessionApi } from '@lib/api/services/guard.api';
import { campusKeys, guardKeys, passKeys } from '@lib/query/keys';
import { formatTime, toServerDateTime } from '@lib/format/datetime';
import type { EntryLogFilterRequest } from '@/types/guard.types';
import { useAuth } from '@hooks/useAuth';

/**
 * Campus Admin screen 1 — the morning screen.
 *
 * FOUR STATS, AND NO "EXITS TODAY". Perimity does not scan people out, so a
 * card implying it would be a lie in the one place an admin quotes numbers
 * from. Denials sit next to entries deliberately: a denial spike is the
 * earliest signal that something is misconfigured — a gate closed by accident,
 * a policy change, an event that ended a day early — and burying it costs a
 * morning.
 *
 * TWO SERVICES FOR TWO NUMBERS, and that is not an accident either. Active
 * passes live in gatepass (Postgres); entries today live in guard (Mongo).
 * One combined endpoint would mean one service reading another's database.
 */
const startOfToday = (): string => toServerDateTime(dayjs().startOf('day'));

export default function AdminOverview() {
  const { campusId, profile } = useAuth();

  /*
   * The window ends at the END of today, not at "now".
   *
   * "now" was recomputed on every render, so it went into the query key and
   * every render asked a slightly different question - and once this screen
   * could page, clicking page 2 would have moved the window at the same time
   * as the page, which is how rows appear to jump between pages.
   *
   * End-of-day is stable for the whole session AND still includes scans that
   * happen while the screen is open, so it is the more correct bound anyway.
   */
  const filter: EntryLogFilterRequest = {
    campusId: campusId ?? 0,
    from: startOfToday(),
    to: toServerDateTime(dayjs().endOf('day')),
  };

  const stats = useQuery({
    queryKey: guardKeys.entryLogStats(filter),
    queryFn: () => entryLogApi.stats(filter),
    enabled: campusId !== null,
  });

  const activePasses = useQuery({
    queryKey: passKeys.count('ACTIVE'),
    queryFn: () => passApi.count('ACTIVE'),
  });

  const gates = useQuery({
    queryKey: campusKeys.gates(campusId ?? 0, false),
    queryFn: () => gateApi.list(campusId as number),
    enabled: campusId !== null,
  });

  /** Reachable by CA and SA — the matcher allows it, unlike most guard routes. */
  const onDuty = useQuery({
    queryKey: guardKeys.openSessions(),
    queryFn: () => sessionApi.open(),
    refetchInterval: 60_000,
  });

  /*
   * Eight a page, and pageable.
   *
   * This card used to ask for the first eight events and stop there, so a
   * campus with a busy morning had no way to see the ninth. The endpoint has
   * always paged - only the card refused to ask for page two.
   *
   * Eight rather than the register's ten: this sits beside "Guards on duty",
   * and the two cards should end at roughly the same place.
   */
  const RECENT_PAGE_SIZE = 8;
  const [recentPage, setRecentPage] = useState(0);
  const recentRequest = { page: recentPage, size: RECENT_PAGE_SIZE };

  const recent = useQuery({
    queryKey: guardKeys.entryLogSearch(filter, recentRequest),
    queryFn: () => entryLogApi.search(filter, recentRequest),
    enabled: campusId !== null,
    // Holds the previous page while the next loads, so the card does not
    // collapse to a skeleton and shove the page around on every click.
    placeholderData: keepPreviousData,
  });

  if (stats.isError) {
    return <ErrorState error={stats.error} onRetry={() => void stats.refetch()} />;
  }

  const openGates = (gates.data ?? []).filter((gate) => gate.active);
  const guardsOnDuty = onDuty.data ?? [];

  /** Things that are wrong right now and nobody is being told. */
  const attention: { text: string; to: string }[] = [];
  if (openGates.length === 0) {
    attention.push({ text: 'No gate is open — nobody can start a shift', to: '/admin/gates' });
  }
  if (guardsOnDuty.length === 0 && openGates.length > 0) {
    attention.push({ text: 'No guard is on duty at any gate', to: '/admin/entry-logs' });
  }

  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      <PageHeader
        title={`Good day${profile?.name ? `, ${profile.name}` : ''}`}
        description={dayjs().format('dddd, D MMMM YYYY')}
      />

      <div className="grid gap-[var(--sp-4)] sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label="Active passes" icon={IdCard}
                  loading={activePasses.isPending} value={activePasses.data ?? 0} />
        <StatCard label="Entries today" icon={ScanLine}
                  loading={stats.isPending} value={stats.data?.entriesPermitted ?? 0} />
        <StatCard label="Refused today" icon={AlertTriangle}
                  loading={stats.isPending} value={stats.data?.deniedCount ?? 0}
                  hint="A spike usually means a configuration change, not an incident." />
        <StatCard label="Gates open" icon={DoorOpen}
                  loading={gates.isPending} value={openGates.length} />
      </div>

      {attention.length > 0 ? (
        <section aria-labelledby="attention" className="surface-card p-[var(--sp-6)]">
          <h2 id="attention" className="text-h3 text-[var(--ink-900)]">Needs attention</h2>
          <ul className="mt-[var(--sp-3)] flex flex-col gap-[var(--sp-2)]">
            {attention.map((item) => (
              <li key={item.text} className="flex items-center justify-between gap-[var(--sp-3)]">
                <span className="text-body flex items-center gap-[var(--sp-2)] text-[var(--ink-700)]">
                  <AlertTriangle className="size-4 shrink-0 text-[var(--review-fg)]" aria-hidden />
                  {item.text}
                </span>
                <Button asChild size="sm" variant="ghost"><Link to={item.to}>Open</Link></Button>
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      <div className="grid gap-[var(--sp-6)] lg:grid-cols-2">
        <section aria-labelledby="on-duty" className="surface-card">
          <h2 id="on-duty" className="text-h3 flex items-center gap-[var(--sp-2)] border-b border-[var(--border)] px-[var(--sp-6)] py-[var(--sp-4)] text-[var(--ink-900)]">
            <Radio className="size-4 text-[var(--ink-500)]" aria-hidden />
            Guards on duty now
          </h2>
          {onDuty.isPending ? (
            <div className="p-[var(--sp-4)]"><Skeleton className="h-12" /></div>
          ) : guardsOnDuty.length > 0 ? (
            <ul className="divide-y divide-[var(--border)]">
              {guardsOnDuty.map((session) => (
                <li key={session.id} className="flex items-center justify-between gap-[var(--sp-3)] px-[var(--sp-6)] py-[var(--sp-3)]">
                  <div className="min-w-0">
                    <p className="text-body-md truncate text-[var(--ink-900)]">{session.gateName}</p>
                    <p className="text-caption text-[var(--ink-500)]">
                      Since {formatTime(session.startedAt)} · {session.totalScans} scans
                    </p>
                  </div>
                  <Badge>On duty</Badge>
                </li>
              ))}
            </ul>
          ) : (
            <EmptyState heading="Nobody is on duty"
                        description="A guard appears here as soon as they start a shift at a gate." />
          )}
        </section>

        <section aria-labelledby="recent" className="surface-card">
          <h2 id="recent" className="text-h3 flex items-center gap-[var(--sp-2)] border-b border-[var(--border)] px-[var(--sp-6)] py-[var(--sp-4)] text-[var(--ink-900)]">
            <ClipboardList className="size-4 text-[var(--ink-500)]" aria-hidden />
            Recent gate events
          </h2>
          {recent.isPending ? (
            <div className="p-[var(--sp-4)]"><Skeleton className="h-12" /></div>
          ) : recent.data && recent.data.items.length > 0 ? (
            <ul className="divide-y divide-[var(--border)]">
              {recent.data.items.map((log) => (
                <li key={log.id} className="flex items-center justify-between gap-[var(--sp-3)] px-[var(--sp-6)] py-[var(--sp-3)]">
                  <div className="min-w-0">
                    <p className="text-body-md truncate text-[var(--ink-900)]">
                      {log.holderName ?? 'Unknown holder'}
                    </p>
                    <p className="text-caption text-[var(--ink-500)]">
                      {log.gateName} · {formatTime(log.scannedAt)}
                    </p>
                  </div>
                  {/* Neutral badges. Verdict colour belongs to the guard's own
                      screen; using it here would train the eye to stop reading
                      green as a decision at the one place it has to be. */}
                  <Badge>
                    {log.scanResult === 'DENIED'
                      ? `Refused · ${(log.denialReason ?? '').replace(/_/g, ' ').toLowerCase()}`
                      : log.scanResult === 'AMBER' ? 'Repeat entry' : 'Allowed'}
                  </Badge>
                </li>
              ))}
            </ul>
          ) : (
            <EmptyState heading="No entries today"
                        description="Scans at any gate appear here as they happen." />
          )}
          {recent.data && (
            <Pagination page={recent.data} onPageChange={setRecentPage} />
          )}
        </section>
      </div>

      <p className="text-caption text-[var(--ink-500)]">
        Entries only — Perimity does not scan people out, so there is no exit count
        anywhere in this product.
      </p>
    </div>
  );
}
