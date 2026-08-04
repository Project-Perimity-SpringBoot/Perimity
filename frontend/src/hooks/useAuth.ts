import { useCallback } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '@stores/authStore';
import { authApi } from '@lib/api/services/auth.api';
import type { Capability } from '@lib/auth/permissions';
import { canReadUserScopedResource, LANDING_ROUTE } from '@lib/auth/permissions';
import type { AuthResponse } from '@/types/auth.types';

export function useAuth() {
  const queryClient = useQueryClient();
  const {
    identity, profile, capabilities, status, signingOut, signIn, signOut, beginSignOut,
  } = useAuthStore();

  const completeSignIn = useCallback(
    (auth: AuthResponse) => {
      signIn(auth.token, auth.expiresAt, auth.user);
      return auth.mustChangePassword ? '/change-password' : LANDING_ROUTE[auth.user?.role ?? 'VISITOR'];
    },
    [signIn],
  );

  const logout = useCallback(async (redirectTo = '/') => {
    /*
     * Sign-out leaves the SPA with a FULL PAGE LOAD, on purpose.
     *
     * Three attempts at doing it in-router all lost the same race: React Router
     * v7 navigates inside startTransition, so the navigation is low priority
     * while a store change is urgent. React renders the emptied store first,
     * still at the old URL, and ProtectedRoute redirects to /login before the
     * navigation ever commits. Reordering the calls cannot fix that, because
     * they are not processed in the order they are written.
     *
     * A location assignment is not scheduled by React at all, so it cannot be
     * beaten. It also guarantees something the router version never could: no
     * residual state anywhere — no cached queries, no timers, no stale
     * subscriptions holding a reference to the session that just ended. For the
     * one action in the app whose entire purpose is to leave nothing behind,
     * that is a feature rather than a compromise.
     *
     * The cost is one page load, on a click that already ends the session.
     *
     * `beginSignOut()` still runs first so the guards hold their fire during
     * the frame or two before the browser unloads, otherwise the login screen
     * flashes on the way out.
     */
    beginSignOut();

    // Fire and forget, while the token is still present. Its outcome must not
    // decide anything: signing out is a local act, and a network failure must
    // never leave somebody apparently signed in.
    void authApi.logout().catch(() => undefined);

    signOut();
    queryClient.clear();

    window.location.assign(redirectTo);
  }, [signOut, beginSignOut, queryClient]);

  return {
    identity,
    profile,
    status,
    isAuthenticated: status === 'authenticated',
    signingOut,
    role: identity?.role ?? null,
    campusId: identity?.campusId ?? null,
    can: (capability: Capability) => capabilities.has(capability),
    canReadUser: (targetUserId: number) =>
      identity ? canReadUserScopedResource(identity, targetUserId) : false,
    completeSignIn,
    logout,
  };
}
