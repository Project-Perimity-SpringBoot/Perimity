import { Navigate, Outlet, useLocation } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { Loader2 } from 'lucide-react';
import { sessionApi } from '@lib/api/services/guard.api';
import { guardKeys } from '@lib/query/keys';
import { useAuth } from '@hooks/useAuth';
import { LANDING_ROUTE, type Capability } from '@lib/auth/permissions';
import type { Role } from '@/types/enums';

function FullPageSpinner() {
  return (
    <div className="flex min-h-dvh items-center justify-center bg-[var(--desk)]">
      <Loader2 className="size-6 animate-spin text-[var(--brand-600)]" aria-label="Loading" />
    </div>
  );
}

/** Signed-in users never see the login screen again by typing its URL. */
export function PublicOnlyRoute() {
  const { isAuthenticated, role } = useAuth();
  if (isAuthenticated && role) return <Navigate to={LANDING_ROUTE[role]} replace />;
  return <Outlet />;
}

export function ProtectedRoute() {
  const { isAuthenticated, status, signingOut } = useAuth();
  const location = useLocation();
  if (status === 'unknown') return <FullPageSpinner />;

  /*
   * A deliberate sign-out is already navigating somewhere. Redirecting to
   * /login here would beat it — see the note on `signingOut` in authStore —
   * and would send a user who chose to leave to a sign-in form instead of home.
   */
  if (signingOut) return null;

  if (!isAuthenticated) {
    return <Navigate to={`/login?next=${encodeURIComponent(location.pathname)}`} replace />;
  }
  return <Outlet />;
}

/**
 * A wrong role is /forbidden, never /login. Sending an authenticated user to
 * the sign-in screen is the bug that makes an app feel broken.
 */
export function RoleRoute({ roles }: { roles: Role[] }) {
  const { role } = useAuth();
  if (!role) return <Navigate to="/login" replace />;
  if (!roles.includes(role)) return <Navigate to="/forbidden" replace />;
  return <Outlet />;
}

export function CapabilityRoute({ capability }: { capability: Capability }) {
  const { can } = useAuth();
  return can(capability) ? <Outlet /> : <Navigate to="/forbidden" replace />;
}

/** A temporary password blocks every other route until it is replaced. */
export function PasswordChangeGate() {
  const { profile } = useAuth();
  const location = useLocation();
  if (profile?.mustChangePassword && location.pathname !== '/change-password') {
    return <Navigate to="/change-password" replace />;
  }
  return <Outlet />;
}

/**
 * A guard with no OPEN session cannot scan — gate and campus come from the
 * session, and without one the server rejects every scan with a 400.
 */
export function GuardSessionGate() {
  const location = useLocation();
  const session = useQuery({
    queryKey: guardKeys.currentSession(),
    queryFn: () => sessionApi.current(),
    retry: false,
    staleTime: 60_000,
  });

  if (session.isPending) return <FullPageSpinner />;

  const onStartScreen = location.pathname === '/guard/session';
  const hasOpenSession = session.data?.state === 'OPEN';

  if (!hasOpenSession && !onStartScreen) return <Navigate to="/guard/session" replace />;
  if (hasOpenSession && onStartScreen) return <Navigate to="/guard" replace />;
  return <Outlet />;
}
