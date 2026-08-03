import { AlertTriangle } from 'lucide-react';
import { Button } from '@ui/index';
import { useAuth } from '@hooks/useAuth';
import { useSessionWatch } from '@hooks/useSessionWatch';

function formatRemaining(seconds: number): string {
  const m = Math.floor(seconds / 60);
  return m >= 1 ? `${m} minute${m === 1 ? '' : 's'}` : `${seconds} seconds`;
}

/**
 * The backend issues no refresh token, so there is no silent recovery — the
 * warning is the entire safety net. A guard's is non-dismissible because a
 * session dying mid-shift stops the scanner at the gate.
 */
export function SessionBanner() {
  const { role } = useAuth();
  const { secondsRemaining, expiringSoon, idleWarning, extendIdle } = useSessionWatch();

  if (idleWarning) {
    return (
      <div
        role="status"
        className="flex items-center justify-between gap-[var(--sp-3)] bg-[var(--review-bg)] px-[var(--sp-4)] py-[var(--sp-2)]"
      >
        <p className="text-small flex items-center gap-[var(--sp-2)] text-[var(--review-fg)]">
          <AlertTriangle className="size-4" aria-hidden />
          You will be signed out shortly for inactivity.
        </p>
        <Button size="sm" variant="secondary" onClick={extendIdle}>
          Stay signed in
        </Button>
      </div>
    );
  }

  if (!expiringSoon || secondsRemaining === null) return null;

  return (
    <div
      role="status"
      aria-live="polite"
      className="flex items-center gap-[var(--sp-2)] bg-[var(--review-bg)] px-[var(--sp-4)] py-[var(--sp-2)]"
    >
      <AlertTriangle className="size-4 shrink-0 text-[var(--review-fg)]" aria-hidden />
      <p className="text-small text-[var(--review-fg)]">
        Your session ends in {formatRemaining(secondsRemaining)}.{' '}
        {role === 'GUARD'
          ? 'End your shift and sign in again before it expires, or scanning will stop.'
          : 'Sign in again to continue without losing work.'}
      </p>
    </div>
  );
}
