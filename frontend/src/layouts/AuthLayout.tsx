import { Link, Outlet } from 'react-router';
import { ShieldCheck, ArrowLeft } from 'lucide-react';
import { Button } from '@ui/index';

/**
 * Every auth screen: one card, centred on the desk background.
 *
 * `showBack` is opt-out rather than opt-in because cancelling is the right
 * affordance on all but one of these routes. The exception is /change-password,
 * which a user holding a temporary password is *required* to complete —
 * PasswordChangeGate would bounce them straight back, so offering a way out
 * there would be a lie.
 */
export function AuthLayout({ showBack = true }: { showBack?: boolean }) {
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-[var(--sp-6)] bg-[var(--desk)] px-[var(--sp-4)] py-[var(--sp-8)]">
      <div className="flex items-center gap-[var(--sp-2)]">
        <ShieldCheck className="size-6 text-[var(--brand-600)]" aria-hidden />
        <span className="text-h2 text-[var(--ink-900)]">Perimity</span>
      </div>

      <div className="w-full max-w-md">
        {showBack && (
          <Button
            asChild
            variant="ghost"
            size="sm"
            className="mb-[var(--sp-2)] -ml-[var(--sp-2)] gap-[var(--sp-2)]"
          >
            <Link to="/">
              <ArrowLeft aria-hidden />
              Back to home
            </Link>
          </Button>
        )}

        <div className="surface-card w-full p-[var(--sp-6)]">
          <Outlet />
        </div>
      </div>

      <p className="text-caption text-[var(--ink-500)]">
        Digital gate passes, verified in seconds.
      </p>
    </div>
  );
}
