import { useEffect, useRef, useState } from 'react';
import { tokenStore } from '@lib/auth/tokenStore';
import { config } from '@lib/config';
import { useAuth } from './useAuth';

const ACTIVITY_EVENTS = ['mousedown', 'keydown', 'scroll', 'touchstart'] as const;
const THROTTLE_MS = 30_000;

export interface SessionWatch {
  /** Seconds until the token expires, or null when signed out. */
  secondsRemaining: number | null;
  expiringSoon: boolean;
  idleWarning: boolean;
  extendIdle: () => void;
}

/**
 * The backend issues no refresh token — 24h absolute expiry, no rotation. So
 * the only safety net is warning the user before the session dies. A guard is
 * the dangerous case: expiry mid-shift stops the scanner at the gate, so their
 * warning starts earlier and idle logout is disabled entirely for them.
 */
export function useSessionWatch(): SessionWatch {
  const { isAuthenticated, role, logout } = useAuth();
  const [secondsRemaining, setSeconds] = useState<number | null>(null);
  const [idleWarning, setIdleWarning] = useState(false);
  const lastActivity = useRef(Date.now());

  const idleDisabled = role === 'GUARD';
  const warnAt = (role === 'GUARD' ? config.guardExpiryWarningMinutes : config.expiryWarningMinutes) * 60;

  useEffect(() => {
    if (!isAuthenticated) {
      setSeconds(null);
      return;
    }
    const tick = () => {
      const expiry = tokenStore.expiresAt();
      if (!expiry) return;
      const remaining = Math.max(0, Math.floor((expiry.getTime() - Date.now()) / 1000));
      setSeconds(remaining);
      if (remaining === 0) void logout();

      if (!idleDisabled) {
        const idleFor = (Date.now() - lastActivity.current) / 60_000;
        if (idleFor >= config.idleTimeoutMinutes) void logout();
        else setIdleWarning(idleFor >= config.idleTimeoutMinutes - 2);
      }
    };
    tick();
    const id = setInterval(tick, 15_000);
    return () => clearInterval(id);
  }, [isAuthenticated, idleDisabled, logout]);

  useEffect(() => {
    if (!isAuthenticated || idleDisabled) return;
    const onActivity = () => {
      const now = Date.now();
      if (now - lastActivity.current < THROTTLE_MS) return;
      lastActivity.current = now;
      setIdleWarning(false);
    };
    ACTIVITY_EVENTS.forEach((e) => window.addEventListener(e, onActivity, { passive: true }));
    return () => ACTIVITY_EVENTS.forEach((e) => window.removeEventListener(e, onActivity));
  }, [isAuthenticated, idleDisabled]);

  return {
    secondsRemaining,
    expiringSoon: secondsRemaining !== null && secondsRemaining <= warnAt,
    idleWarning,
    extendIdle: () => {
      lastActivity.current = Date.now();
      setIdleWarning(false);
    },
  };
}
