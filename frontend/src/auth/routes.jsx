import { ROLES } from '../shared/roles';
import GuardLogin from './GuardLogin';
import GateSessionStart from './GateSessionStart';
import SessionEnded from './SessionEnded';

/**
 * Signed-in auth routes only — /login, /otp and /change-password are public
 * and stay in App.jsx, because a route table that needs auth to describe the
 * login page is a circular problem.
 */
export const authRoutes = [];

export const authFullBleedRoutes = [
  // Kept as the first-run shift screen. /guard/gate (Day 18) is the live one
  // that also handles switching; this one stays as the post-login landing.
  { path: '/guard/start-shift', element: <GateSessionStart />, allow: [ROLES.GUARD] },
  { path: '/guard/ended',       element: <SessionEnded />,     allow: [ROLES.GUARD] },
];

export const publicAuthRoutes = [
  { path: '/guard/login', element: <GuardLogin /> },
];
