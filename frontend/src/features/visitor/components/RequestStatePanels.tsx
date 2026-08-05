import { Link } from 'react-router';
import { CalendarPlus, Clock, FileX2 } from 'lucide-react';
import { Button } from '@ui/index';
import { EmptyState } from '@components/feedback';
import { LifecycleStrip } from '@components/pass';
import { formatDate, formatValidity } from '@lib/format/datetime';
import type { VisitorRequestResponse } from '@/types/gatepass.types';

/**
 * Three of the visitor dashboard's five states. The other two render a
 * PassCard and live in the dashboard itself.
 *
 * ==========================================================================
 * MUTUALLY EXCLUSIVE, NOT WIDGETS
 * ==========================================================================
 * Exactly one of these is on screen at a time. They are separate components
 * rather than conditional blocks inside one layout because the states have
 * nothing in common: "you have no request" and "here is your pass" share no
 * heading, no action and no shape. A single layout with five conditionals
 * would grow a sixth hybrid state the first time someone forgot an else.
 */

/** State 1. The genuine first-run state — most visitors arrive here. */
export function NoRequestState({ email }: { email: string | undefined }) {
  return (
    <EmptyState
      icon={CalendarPlus}
      heading="You have no visitor pass yet"
      description={
        email
          ? `Signed in as ${email}. Apply below and your host will be asked to approve it.`
          : 'Apply below and your host will be asked to approve it.'
      }
      action={
        <Button asChild>
          <Link to="/visitor/apply">Apply for a visitor pass</Link>
        </Button>
      }
    />
  );
}

/**
 * State 2. Waiting on the host.
 *
 * The LifecycleStrip shows PENDING lit so the visitor can see where they are
 * in a process they cannot otherwise observe. Nothing here is actionable on
 * purpose: chasing is the host's job to answer, not the visitor's to do.
 */
export function PendingState({ request }: { request: VisitorRequestResponse }) {
  return (
    <section className="surface-card flex flex-col gap-[var(--sp-4)] p-[var(--sp-6)]">
      <div className="flex flex-col gap-[var(--sp-1)]">
        <h2 className="text-h3 text-[var(--ink-900)]">Waiting for approval</h2>
        <p className="text-body text-[var(--ink-700)]">
          Your request for {formatValidity(request.visitFrom, request.visitTo)} is with
          your host.
        </p>
      </div>

      <LifecycleStrip current="PENDING" />

      <dl className="grid gap-[var(--sp-3)] sm:grid-cols-2">
        <div>
          <dt className="text-label text-[var(--ink-500)]">Request</dt>
          {/* Mono because it is a code somebody may read aloud at a gate. */}
          <dd className="text-mono text-[var(--ink-900)]">#{request.id}</dd>
        </div>
        <div>
          <dt className="text-label text-[var(--ink-500)]">Submitted</dt>
          <dd className="text-body text-[var(--ink-900)]">{formatDate(request.createdAt)}</dd>
        </div>
        <div className="sm:col-span-2">
          <dt className="text-label text-[var(--ink-500)]">Purpose</dt>
          <dd className="text-body text-[var(--ink-900)]">{request.purpose}</dd>
        </div>
      </dl>

      <p className="text-caption text-[var(--ink-500)]">
        You will be emailed either way. If it is approved, your pass and its QR
        arrive in the same message — there is nothing to check back for.
      </p>
    </section>
  );
}

/**
 * State 3. Rejected.
 *
 * ==========================================================================
 * NEUTRAL STYLING, DELIBERATELY NOT RED
 * ==========================================================================
 * Green, red and amber belong to the guard's scan verdict screens and appear
 * nowhere else in the product. A red panel here would also be wrong on its own
 * terms: a declined request is a routine answer — the lab is closed, the dates
 * do not suit — not a failure or a warning. The word "Not approved" carries
 * the meaning; the colour does not need to.
 *
 * The host's reason is shown verbatim and given the most space. It is the one
 * thing that stops the visitor filing the identical request again.
 */
export function RejectedState({ request }: { request: VisitorRequestResponse }) {
  return (
    <section className="surface-card flex flex-col gap-[var(--sp-4)] p-[var(--sp-6)]">
      <div className="flex items-start gap-[var(--sp-3)]">
        <FileX2 className="mt-[2px] size-5 shrink-0 text-[var(--ink-500)]" aria-hidden />
        <div className="flex flex-col gap-[var(--sp-1)]">
          <h2 className="text-h3 text-[var(--ink-900)]">Your request was not approved</h2>
          <p className="text-caption text-[var(--ink-500)]">
            Decided {request.reviewedAt ? formatDate(request.reviewedAt) : 'recently'}.
          </p>
        </div>
      </div>

      {request.rejectReason ? (
        <div className="rounded-[var(--r-md)] bg-[var(--surface-sunken)] p-[var(--sp-4)]">
          <p className="text-label mb-[var(--sp-1)] text-[var(--ink-500)]">
            Your host&rsquo;s reason
          </p>
          <p className="text-body text-[var(--ink-900)]">{request.rejectReason}</p>
        </div>
      ) : null}

      <p className="text-body text-[var(--ink-700)]">
        You can apply again — different dates or a different host often resolve it.
      </p>

      <div>
        <Button asChild>
          <Link to="/visitor/apply">Submit a new request</Link>
        </Button>
      </div>
    </section>
  );
}

/** Shown under a pass when the visit has not started yet. */
export function NotStartedYetNote({ validFrom }: { validFrom: string }) {
  return (
    <p className="text-caption flex items-center gap-[var(--sp-2)] text-[var(--ink-500)]">
      <Clock className="size-4 shrink-0" aria-hidden />
      This pass starts on {formatDate(validFrom)}. It will not scan before then.
    </p>
  );
}
