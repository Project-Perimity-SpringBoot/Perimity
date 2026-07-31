import { useCallback, useEffect, useRef, useState } from 'react';
import { guard } from '../api';

/**
 * The shift, kept alive across a long one.
 *
 * Day 18's gate is "no re-login across a shift". A guard's shift is eight
 * hours; a JWT is not. Two things happen here:
 *
 *  1. The session is re-fetched every five minutes. That both confirms the
 *     shift is still open and exercises the token before it is needed —
 *     finding out the session died at 03:00 while nobody is at the gate is
 *     far better than finding out mid-scan with a queue waiting.
 *  2. The session is cached in localStorage so a refresh, a dropped phone or
 *     an accidental back-swipe does not send the guard back to gate selection.
 *
 * It deliberately does NOT auto-renew a token. Silently extending a session
 * forever is a different decision, and one nobody made.
 */
const KEY = 'perimity.guard.session';
const REFRESH_MS = 5 * 60 * 1000;

export function useGuardSession() {
  const [session, setSession] = useState(() => {
    try { return JSON.parse(localStorage.getItem(KEY)) || null; } catch { return null; }
  });
  const [checking, setChecking] = useState(true);
  const timer = useRef(null);

  const store = useCallback((s) => {
    setSession(s);
    if (s) localStorage.setItem(KEY, JSON.stringify(s));
    else localStorage.removeItem(KEY);
  }, []);

  const refresh = useCallback(async () => {
    try {
      const s = await guard.currentSession();
      store(s ?? null);
    } catch (e) {
      // A 404 means no open shift — a real answer, not a failure. Anything
      // else is a network blip and the cached session stays put, because
      // dropping a guard out of their shift over one failed poll is worse
      // than showing a slightly stale gate name.
      if (e.status === 404) store(null);
    } finally {
      setChecking(false);
    }
  }, [store]);

  useEffect(() => {
    refresh();
    timer.current = setInterval(refresh, REFRESH_MS);
    return () => clearInterval(timer.current);
  }, [refresh]);

  const start = useCallback(async (campusId, gateId, gateName) => {
    const s = await guard.startSession(campusId, gateId, gateName, {
      userAgent: navigator.userAgent.slice(0, 120),
      appVersion: import.meta.env.VITE_APP_VERSION ?? 'dev',
    });
    store(s);
    return s;
  }, [store]);

  const end = useCallback(async () => {
    if (session?.id) await guard.endSession(session.id);
    store(null);
  }, [session, store]);

  return { session, checking, start, end, refresh };
}
