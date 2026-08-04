import { Link } from 'react-router';
import { MailCheck } from 'lucide-react';
import { Button } from '@ui/index';
import { PageHeader } from '@components/data';
import { LifecycleStrip } from '@components/pass';
import { OTP_RULES } from '@lib/validation/patterns';

/**
 * Phase 5 screen 3 — the request landed.
 *
 * A separate route rather than a toast on the dashboard. The visitor has just
 * finished the only form in this product and the next thing that happens to
 * them happens by email, hours later, somewhere else. A toast that vanishes in
 * four seconds is the wrong medium for "nothing else is required of you".
 *
 * It deliberately does NOT show a request id as the headline. The visitor does
 * not need to quote one at anybody — the email is the thread — and leading with
 * a reference number implies a support process that does not exist.
 *
 * No "check back later" instruction either. There is nothing to check: the
 * pass arrives by email, and telling somebody to return to a page that will
 * look identical until a stranger acts is asking them to do the system's job.
 */
export default function RequestSubmittedPage() {
  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      <PageHeader title="Request sent" />

      <section className="surface-card flex flex-col gap-[var(--sp-5)] p-[var(--sp-6)]">
        <div className="flex items-start gap-[var(--sp-3)]">
          <MailCheck className="mt-[2px] size-5 shrink-0 text-[var(--ink-700)]" aria-hidden />
          <div className="flex flex-col gap-[var(--sp-1)]">
            <h2 className="text-h3 text-[var(--ink-900)]">Your host has been notified</h2>
            <p className="text-body text-[var(--ink-700)]">
              There is nothing else for you to do. You will be emailed whether it is
              approved or not.
            </p>
          </div>
        </div>

        <LifecycleStrip current="PENDING" />

        <div className="rounded-[var(--r-md)] bg-[var(--surface-sunken)] p-[var(--sp-4)]">
          <h3 className="text-label mb-[var(--sp-2)] text-[var(--ink-500)]">What happens next</h3>
          <ol className="text-body flex list-decimal flex-col gap-[var(--sp-2)] pl-[var(--sp-4)] text-[var(--ink-900)]">
            <li>Your host reviews the dates and the purpose.</li>
            <li>If approved, your pass and its QR arrive by email as a PDF.</li>
            <li>Show the QR at any gate. Entry is scanned on the way in.</li>
          </ol>
        </div>

        {/*
         * The recovery story, stated once and plainly. A one-time visitor who
         * loses the email has no account to log into and no password to reset —
         * they come back, enter the same address, and get a code. Saying so
         * here is cheaper than a support conversation later.
         */}
        <p className="text-caption text-[var(--ink-500)]">
          Lost the email? Come back to this site and sign in with the same address —
          a {OTP_RULES.length}-digit code brings your pass straight back.
        </p>

        <div className="flex flex-wrap gap-[var(--sp-3)]">
          <Button asChild>
            <Link to="/visitor">Back to your pass</Link>
          </Button>
        </div>
      </section>
    </div>
  );
}
