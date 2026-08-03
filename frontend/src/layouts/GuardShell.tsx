import { Suspense } from 'react';
import { Outlet } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { LogOut, Radio } from 'lucide-react';
import { Button, Skeleton } from '@ui/index';
import { sessionApi } from '@lib/api/services/guard.api';
import { guardKeys } from '@lib/query/keys';
import { formatTime } from '@lib/format/datetime';
import { useAuth } from '@hooks/useAuth';
import { ErrorBoundary } from '@components/feedback';
import { SessionBanner } from './SessionBanner';

/**
 * Darker, larger targets, built for a phone held one-handed at a gate in bright
 * sun. data-shell="guard" swaps the surface tokens; contrast targets 7:1.
 */
export function GuardShell() {
  const { logout } = useAuth();
  const session = useQuery({
    queryKey: guardKeys.currentSession(),
    queryFn: () => sessionApi.current(),
    retry: false,
    staleTime: 60_000,
  });

  return (
    <div data-shell="guard" className="flex min-h-dvh flex-col bg-[var(--desk)] text-[var(--ink-900)]">
      {/* Fixed and visible on every guard screen without exception. The guard
          must never be unsure which gate they are bound to. */}
      <header
        className="sticky top-0 z-40 flex items-center justify-between gap-[var(--sp-3)] border-b border-[var(--border)] bg-[var(--surface)] px-[var(--sp-4)]"
        style={{ minHeight: 'var(--guard-bar-h)' }}
      >
        {session.isPending ? (
          <Skeleton className="h-4 w-48" />
        ) : session.data ? (
          <p className="text-small flex items-center gap-[var(--sp-2)] text-[var(--ink-900)]">
            <Radio className="size-4 text-[var(--allow-solid)]" aria-hidden />
            <span className="font-semibold">{session.data.gateName}</span>
            <span className="text-[var(--ink-500)]">·</span>
            <span className="text-mono">shift {formatTime(session.data.startedAt)}</span>
          </p>
        ) : (
          <p className="text-small text-[var(--ink-500)]">No open shift</p>
        )}

        <Button variant="ghost" size="sm" onClick={() => void logout()} aria-label="Sign out">
          <LogOut aria-hidden />
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
