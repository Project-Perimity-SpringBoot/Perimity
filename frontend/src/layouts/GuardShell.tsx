import { Suspense } from 'react';
import { Outlet } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { LogOut, Radio, ScanLine } from 'lucide-react';
import { Button, Skeleton } from '@ui/index';
import { sessionApi } from '@lib/api/services/guard.api';
import { guardKeys } from '@lib/query/keys';
import { formatTime } from '@lib/format/datetime';
import { useAuth } from '@hooks/useAuth';
import { ErrorBoundary } from '@components/feedback';
import { SessionBanner } from './SessionBanner';

/**
 * The guard console shell.
 *
 * ==========================================================================
 * data-shell="guard" IS GONE. READ THIS BEFORE PUTTING IT BACK.
 * ==========================================================================
 * That attribute swapped the surface tokens to a dark treatment, chosen for a
 * phone held one-handed at a gate in bright sun. The console is light now
 * because that is what was asked for, and it does look better indoors.
 *
 * The trade is real and worth knowing: this is the one screen genuinely used
 * outdoors, and a white card at midday is harder to read than a dark one. If a
 * demo or a real gate turns out to need it, the fix is one attribute on the
 * div below — `data-shell="guard"` — and nothing else changes, because every
 * colour here is a token.
 *
 * The 56px targets did NOT depend on that attribute and are unaffected.
 */
export function GuardShell() {
  const { logout, profile, identity } = useAuth();
  const name = profile?.name ?? identity?.name ?? identity?.email ?? 'guard';

  const session = useQuery({
    queryKey: guardKeys.currentSession(),
    queryFn: () => sessionApi.current(),
    retry: false,
    staleTime: 60_000,
  });

  return (
    <div className="flex min-h-dvh flex-col bg-[var(--surface-subtle)] text-[var(--ink-900)]">
      {/* Fixed and visible on every guard screen without exception. The guard
          must never be unsure which gate they are bound to — that is why the
          gate name survived the restyle rather than being traded for the
          tidier "Logged in as …" line on its own. */}
      <header
        className="sticky top-0 z-40 flex items-center justify-between gap-[var(--sp-3)] border-b border-[var(--border)] bg-[var(--surface)] px-[var(--sp-4)] py-[var(--sp-3)]"
        style={{ minHeight: 'var(--guard-bar-h)' }}
      >
        <div className="flex min-w-0 items-center gap-[var(--sp-3)]">
          <span className="flex size-11 shrink-0 items-center justify-center rounded-[var(--r-md)] bg-[var(--brand-600)]">
            <ScanLine className="size-6 text-white" aria-hidden />
          </span>

          <div className="min-w-0">
            <p className="text-h3 truncate text-[var(--ink-900)]">Guard Scanner</p>

            {session.isPending ? (
              <Skeleton className="mt-[var(--sp-1)] h-3 w-40" />
            ) : session.data ? (
              <p className="text-caption flex flex-wrap items-center gap-x-[var(--sp-2)] text-[var(--ink-500)]">
                <span className="truncate">Signed in as {name}</span>
                <span aria-hidden>·</span>
                <span className="flex items-center gap-[var(--sp-1)] text-[var(--ink-700)]">
                  <Radio className="size-3 text-[var(--allow-solid)]" aria-hidden />
                  <span className="font-semibold">{session.data.gateName}</span>
                </span>
                <span aria-hidden>·</span>
                <span className="text-mono">shift {formatTime(session.data.startedAt)}</span>
              </p>
            ) : (
              <p className="text-caption text-[var(--ink-500)]">
                Signed in as {name} · no open shift
              </p>
            )}
          </div>
        </div>

        <Button
          variant="ghost"
          onClick={() => void logout()}
          className="min-h-14 shrink-0 gap-[var(--sp-2)]"
        >
          <LogOut aria-hidden />
          <span className="hidden sm:inline">Logout</span>
        </Button>
      </header>

      <SessionBanner />

      <main className="flex-1">
        <ErrorBoundary region="guard">
          <Suspense fallback={<Skeleton className="m-[var(--sp-4)] h-64" />}>
            <Outlet />
          </Suspense>
        </ErrorBoundary>
      </main>
    </div>
  );
}
