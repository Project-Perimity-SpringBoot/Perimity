import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { authApi, tokenStore, setUnauthorizedHandler } from '../api/client';

/**
 * Who is signed in, for the whole app.
 *
 * The token lives in localStorage and the USER OBJECT DOES NOT. On every
 * reload the shell calls GET /api/auth/me and rebuilds it from the server.
 * Caching the user in storage as well would mean a role change, a lockout or
 * a deactivation kept working until the tab was closed - the browser would be
 * asserting permissions the server had already withdrawn.
 *
 * On localStorage: readable by any script on this origin, so an XSS becomes a
 * stolen token. The alternative - keeping it in memory only - logs the user
 * out on every refresh, which for a guard on a gate is worse. Worth being able
 * to state the trade rather than pretending there isn't one.
 */
const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  const logout = useMemo(
    () => async ({ callServer = true } = {}) => {
      // Best effort. FR-SESS-2 denylists the token server-side, but a failed
      // logout call must still clear the client - otherwise a network blip
      // leaves someone logged in who pressed Log out.
      if (callServer && tokenStore.get()) {
        try {
          await authApi.post('/api/auth/logout');
        } catch {
          /* ignore - clearing locally is what matters */
        }
      }
      tokenStore.clear();
      setUser(null);
    },
    [],
  );

  // Lets the axios interceptor end the session on any 401.
  useEffect(() => {
    setUnauthorizedHandler(() => logout({ callServer: false }));
  }, [logout]);

  // Rebuild the session on load or refresh.
  useEffect(() => {
    let cancelled = false;
    async function restore() {
      if (!tokenStore.get()) {
        setLoading(false);
        return;
      }
      try {
        const me = await authApi.get('/api/auth/me');
        if (!cancelled) setUser(me);
      } catch {
        tokenStore.clear();
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    restore();
    return () => {
      cancelled = true;
    };
  }, []);

  /** Both login paths end here: store the token, then trust the server for the user. */
  const acceptAuthResponse = (auth) => {
    tokenStore.set(auth.token);
    setUser(auth.user);
    return auth;
  };

  const loginWithPassword = async (email, password) =>
    acceptAuthResponse(await authApi.post('/api/auth/login', { email, password }));

  const requestOtp = (email, purpose = 'LOGIN', campusId = null) =>
    authApi.post('/api/auth/otp/request', { email, purpose, campusId });

  const verifyOtp = async (email, code, purpose = 'LOGIN') =>
    acceptAuthResponse(await authApi.post('/api/auth/otp/verify', { email, code, purpose }));

  const changePassword = async (currentPassword, newPassword, confirmPassword) => {
    await authApi.post('/api/auth/password/change', {
      currentPassword,
      newPassword,
      confirmPassword,
    });
    // The flag lives on the server; re-read rather than guessing locally.
    setUser(await authApi.get('/api/auth/me'));
  };

  const value = {
    user,
    loading,
    isAuthenticated: !!user,
    loginWithPassword,
    requestOtp,
    verifyOtp,
    changePassword,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
