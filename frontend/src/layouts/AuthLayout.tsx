import { Outlet } from 'react-router';
import { ShieldCheck } from 'lucide-react';

/** Every auth screen: one card, centred on the desk background. */
export function AuthLayout() {
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-[var(--sp-6)] bg-[var(--desk)] px-[var(--sp-4)] py-[var(--sp-8)]">
      <div className="flex items-center gap-[var(--sp-2)]">
        <ShieldCheck className="size-6 text-[var(--brand-600)]" aria-hidden />
        <span className="text-h2 text-[var(--ink-900)]">Perimity</span>
      </div>

      <div className="surface-card w-full max-w-md p-[var(--sp-6)]">
        <Outlet />
      </div>

      <p className="text-caption text-[var(--ink-500)]">
        Digital gate passes, verified in seconds.
      </p>
    </div>
  );
}
