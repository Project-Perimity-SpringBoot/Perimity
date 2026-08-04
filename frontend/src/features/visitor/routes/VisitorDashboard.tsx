import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { Button, SkeletonText } from '@ui/index';
import { ErrorState } from '@components/feedback';
import { PageHeader } from '@components/data';
import { PassCard } from '@components/pass';
import { visitorRequestApi, passApi } from '@lib/api/services/gatepass.api';
import { requestKeys, passKeys } from '@lib/query/keys';
import { useAuth } from '@hooks/useAuth';
import type { GatePassResponse, VisitorRequestResponse } from '@/types/gatepass.types';
import { NoRequestState, PendingState, RejectedState, NotStartedYetNote } from '../components/RequestStatePanels';

/**
 * Phase 5 screen 1 — the visitor's home.
 *
 * ==========================================================================
 * STATE-DRIVEN, NOT STAT-DRIVEN
 * ==========================================================================
 * Five mutually exclusive states, one on screen at a time:
 *
 *   no request · pending · rejected · approved · event attendee
 *
 * NOT one layout with conditional widgets. A visitor is not a dashboard user —
 * they are here to answer one question ("do I have a pass, and can I get in?")
 * and then leave. Stat tiles, activity feeds and side panels would all be
 * answering questions nobody asked.
 *
 * The decision order below matters. A pass outranks a request: once one is
 * issued, the request that produced it is history and showing "pending" beside
 * a working pass would be wrong. Among requests, the newest wins.
 *
 * ==========================================================================
 * NO PASSWORD ANYWHERE ON THIS ROLE
 * ==========================================================================
 * A visitor authenticates by email OTP and never sets a password. Nothing on
 * any visitor screen may offer one — including a forgot-password link inherited
 * from a shared component. Worth re-checking whenever something shared changes.
 */
export default function VisitorDashboard() {
  const { identity } = useAuth();

  const passes = useQuery({
    queryKey: passKeys.mine(),
    queryFn: () => passApi.mine(),
  });

  /**
   * my-history rather than /mine: /mine is the host's paged queue and answers
   * "requests naming me as host", which for a visitor is always empty.
   */
  const requests = useQuery({
    queryKey: requestKeys.myHistory(),
    queryFn: () => visitorRequestApi.myHistory(),
  });

  if (passes.isError) {
    return <ErrorState error={passes.error} onRetry={() => void passes.refetch()} />;
  }
  if (requests.isError) {
    return <ErrorState error={requests.error} onRetry={() => void requests.refetch()} />;
  }

  const loading = passes.isPending || requests.isPending;
  const allPasses = passes.data ?? [];
  const allRequests = requests.data ?? [];

  const greeting = identity?.name ? `Welcome, ${identity.name.split(' ')[0]}` : 'Your visitor pass';

  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      <PageHeader
        title={greeting}
        description="Show your QR at the gate. Entry is scanned on the way in."
      />

      {loading ? (
        <div className="surface-card p-[var(--sp-6)]"><SkeletonText lines={5} /></div>
      ) : (
        <DashboardState passes={allPasses} requests={allRequests} email={identity?.email} />
      )}
    </div>
  );
}

/**
 * The single decision point. Exactly one branch returns.
 *
 * Written as one function with early returns rather than a chain of `&&`
 * blocks in the JSX, so that "which state am I in" is answerable by reading
 * top to bottom, and so two states can never render at once.
 */
function DashboardState({
  passes, requests, email,
}: {
  passes: GatePassResponse[];
  requests: VisitorRequestResponse[];
  email: string | undefined;
}) {
  /* ── States 4 and 5. A pass outranks any request that produced it. ── */
  const pass = pickPass(passes);
  if (pass) {
    const isEvent = pass.passType === 'EVENT';
    const hasBoth = isEvent && passes.some((p) => p.passType === 'DAILY');

    return (
      <section className="flex flex-col gap-[var(--sp-4)]">
        <PassCard pass={pass} variant="detail" />

        {pass.status === 'PENDING' ? <NotStartedYetNote validFrom={pass.validFrom} /> : null}

        <p className="text-body text-[var(--ink-700)]">
          {isEvent
            ? 'Use this QR for the programme.'
            : 'Show this QR at the gate. A copy was emailed to you.'}
        </p>

        {/*
         * Only when the holder genuinely has both. Someone carrying a daily
         * pass and an event pass will scan whichever is on top, and the guard
         * cannot see the difference — the server attributes the entry to the
         * event either way. Saying so stops a visitor hunting for "the right
         * one" at a gate with a queue behind them.
         */}
        {hasBoth ? (
          <p className="text-caption text-[var(--ink-500)]">
            You also hold a daily pass. Either QR works, and your entry is recorded
            against the event.
          </p>
        ) : null}

        <div>
          <Button variant="secondary" asChild>
            <Link to="/visitor/pass">Open full pass</Link>
          </Button>
        </div>
      </section>
    );
  }

  /* ── States 2 and 3. Newest request decides. ── */
  const latest = pickLatestRequest(requests);
  if (latest?.status === 'PENDING') return <PendingState request={latest} />;
  if (latest?.status === 'REJECTED') return <RejectedState request={latest} />;

  /* ── State 1. No request, or only cancelled ones. ── */
  return <NoRequestState email={email} />;
}

/**
 * An EVENT pass wins over a DAILY one when both exist: the event is why the
 * visitor is on campus today, and it is the pass whose dates they need to see.
 * Revoked and expired passes are never the headline — falling through to the
 * request states tells the visitor something actionable instead.
 */
function pickPass(passes: GatePassResponse[]): GatePassResponse | undefined {
  const usable = passes.filter((p) => p.status === 'ACTIVE' || p.status === 'PENDING');
  return usable.find((p) => p.passType === 'EVENT') ?? usable[0];
}

/**
 * Newest by createdAt. Server timestamps are zone-less, so they are compared
 * as strings — ISO-8601 without a zone sorts correctly lexicographically, and
 * parsing them into Dates here would be the exact mistake
 * parseServerDateTime exists to prevent.
 */
function pickLatestRequest(requests: VisitorRequestResponse[]): VisitorRequestResponse | undefined {
  return [...requests]
    .filter((r) => r.status !== 'CANCELLED')
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))[0];
}
