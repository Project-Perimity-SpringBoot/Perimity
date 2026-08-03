import { create } from 'zustand';
import { tokenStore } from '@lib/auth/tokenStore';
import type { Identity } from '@lib/auth/claims';
import type { UserResponse } from '@/types/auth.types';
import { capabilitiesFor, type Capability } from '@lib/auth/permissions';

interface AuthState {
  identity: Identity | null;
  /** From GET /api/auth/me. Richer than the token claims and authoritative. */
  profile: UserResponse | null;
  capabilities: ReadonlySet<Capability>;
  status: 'unknown' | 'authenticated' | 'anonymous';
  /**
   * True between the moment a sign-out starts and the moment the router has
   * moved off the protected route.
   *
   * React Router v7 wraps navigate() in startTransition, so the navigation is
   * a LOW-PRIORITY update while a Zustand store change is urgent. React
   * therefore renders the emptied store first, still at the old location —
   * ProtectedRoute sees an unauthenticated user on /platform and redirects to
   * /login?next=/platform, which supersedes the pending navigation.
   *
   * The guards read this flag and hold their fire. It is a scheduling problem,
   * so the fix is explicit sequencing rather than hoping the two updates land
   * in a helpful order.
   */
  signingOut: boolean;
  beginSignOut: () => void;
  signIn: (token: string, expiresAt: string, user: UserResponse | null) => void;
  setProfile: (user: UserResponse) => void;
  signOut: () => void;
  /** Re-reads tokenStore after a cross-tab logout or a 401 interceptor clear. */
  sync: () => void;
}

function snapshot(profile: UserResponse | null) {
  const identity = tokenStore.identity();
  return {
    identity,
    profile,
    capabilities: capabilitiesFor(identity),
    status: (identity ? 'authenticated' : 'anonymous') as AuthState['status'],
  };
}

export const useAuthStore = create<AuthState>((set, get) => ({
  ...snapshot(null),
  status: tokenStore.isAuthenticated() ? 'authenticated' : 'unknown',
  signingOut: false,

  beginSignOut: () => set({ signingOut: true }),

  signIn: (token, expiresAt, user) => {
    tokenStore.set(token, expiresAt);
    set({ ...snapshot(user), signingOut: false });
  },

  setProfile: (profile) => set(snapshot(profile)),

  signOut: () => {
    tokenStore.clear();
    // `signingOut` is deliberately NOT lowered here. Lowering it in the same
    // synchronous block as the clear means React batches both into one render
    // where the flag is already down — which is exactly the bug it exists to
    // prevent. signIn() resets it; a full-page sign-out discards the store
    // anyway.
    set({ identity: null, profile: null, capabilities: new Set(), status: 'anonymous' });
  },

  sync: () => set(snapshot(get().profile)),
}));

// The interceptor and other tabs clear the token without going through the
// store; this keeps React in step with the single source of truth.
tokenStore.subscribe(() => useAuthStore.getState().sync());
