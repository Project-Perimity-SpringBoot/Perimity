import { Clock, ShieldAlert } from 'lucide-react';

/**
 * Batch 1 screen 9 — account locked, and session expired.
 *
 * These are the two states that are NOT "you typed it wrong", and both were
 * previously indistinguishable from a wrong password: same red box, same
 * position, same weight. That is the failure this fixes.
 *
 * A LOCKOUT IS A WAIT, NOT A RETRY. Telling someone "invalid credentials" when
 * the real answer is "come back at 14:05" makes them try four more times, which
 * on some backends extends the lockout. auth-service already puts the unlock
 * time in the message; this surfaces it as its own panel with no call to
 * action, because there is nothing useful to do but wait.
 *
 * AN EXPIRED SESSION IS NOT A FAILURE. It is the normal end of a 24-hour token
 * — there is no refresh anywhere in this backend — so it reads as information,
 * not as an error.
 */

/**
 * auth-service returns a 401 for a wrong password AND for a locked account, so
 * the status alone cannot tell them apart. The locked message is the only one
 * carrying a time, which is what this matches. Deliberately loose: if the
 * wording changes server-side the worst case is the generic red box, never a
 * crash or a wrong claim about the account.
 */
export function isLockoutMessage(message: string): boolean {
  return /temporarily locked/i.test(message) && /\d{1,2}:\d{2}/.test(message);
}

export function AccountLockedNotice({ message }: { message: string }) {
  const time = /(\d{1,2}:\d{2}(?::\d{2})?)/.exec(message)?.[1];

  return (
    <div
      role="alert"
      className="flex items-start gap-[var(--sp-3)] rounded-[var(--r-sm)] border border-[var(--review-solid)] bg-[var(--review-bg)] px-[var(--sp-3)] py-[var(--sp-3)]"
    >
      <ShieldAlert className="mt-[2px] size-5 shrink-0 text-[var(--review-fg)]" aria-hidden />
      <div>
        <p className="text-body-md text-[var(--review-fg)]">This account is locked</p>
        <p className="text-small mt-[var(--sp-1)] text-[var(--review-fg)]">
          {time
            ? `Too many failed attempts. Try again after ${time}.`
            : 'Too many failed attempts. Try again shortly.'}
        </p>
        <p className="text-caption mt-[var(--sp-2)] text-[var(--review-fg)]">
          Signing in again before then will not work. Your campus administrator can
          unlock it sooner.
        </p>
      </div>
    </div>
  );
}

export function SessionExpiredNotice() {
  return (
    <div
      role="status"
      className="flex items-start gap-[var(--sp-3)] rounded-[var(--r-sm)] bg-[var(--surface-sunken)] px-[var(--sp-3)] py-[var(--sp-3)]"
    >
      <Clock className="mt-[2px] size-5 shrink-0 text-[var(--ink-500)]" aria-hidden />
      <div>
        <p className="text-body-md text-[var(--ink-900)]">Your session ended</p>
        <p className="text-small mt-[var(--sp-1)] text-[var(--ink-700)]">
          Sessions last 24 hours and cannot be extended. Sign in again to carry on —
          nothing you did was lost.
        </p>
      </div>
    </div>
  );
}
