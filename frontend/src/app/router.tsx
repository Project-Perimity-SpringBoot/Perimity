import { lazy } from 'react';
import { createBrowserRouter, Navigate } from 'react-router';
import { AppShell, AuthLayout, GuardShell } from '@/layouts';
import { PhasePending } from '@components/feedback';
import {
  GuardSessionGate, PasswordChangeGate, ProtectedRoute, PublicOnlyRoute, RoleRoute,
} from './guards';

/**
 * PHASE 0 + PHASE 1 ONLY.
 *
 * Every feature entry is lazily loaded. This is the highest-value performance
 * decision in the project and it comes from the deployment context rather than
 * a score: a guard on a phone in bad signal at a gate must not download the
 * Campus Admin audit table to scan a pass.
 *
 * ── FOR EVERY OTHER PHASE OWNER ──────────────────────────────────────────────
 * This file is Omkar's. To add your screens:
 *   1. Add a `const X = lazy(() => import('@features/<yours>/routes/X'));`
 *   2. Replace your <PhasePending /> element with your real route children.
 * Post both lines in the group — the edit takes ten minutes and keeps one
 * person responsible for the guard composition, which is the part that is easy
 * to get subtly wrong.
 *
 * Guard composition, outermost to innermost:
 *   PublicOnlyRoute    → an authenticated user is sent to their landing route
 *   ProtectedRoute     → valid unexpired token, else /login?next=…
 *   PasswordChangeGate → mustChangePassword locks every route but /change-password
 *   RoleRoute          → wrong role → their OWN dashboard, never a 403 page
 *   GuardSessionGate   → GUARD with no OPEN session → /guard/session
 */
const HomePage = lazy(() => import('@features/public/routes/HomePage'));
const ForbiddenPage = lazy(() => import('@features/public/routes/ForbiddenPage'));
const NotFoundPage = lazy(() => import('@features/public/routes/NotFoundPage'));

const LoginPage = lazy(() => import('@features/auth/routes/LoginPage'));
const EmailCodePage = lazy(() => import('@features/auth/routes/EmailCodePage'));
const VerifyCodePage = lazy(() => import('@features/auth/routes/VerifyCodePage'));
const ForgotPasswordPage = lazy(() => import('@features/auth/routes/ForgotPasswordPage'));
const ResetPasswordPage = lazy(() => import('@features/auth/routes/ResetPasswordPage'));
const ChangePasswordPage = lazy(() => import('@features/auth/routes/ChangePasswordPage'));
const VisitorRegisterPage = lazy(() => import('@features/auth/routes/VisitorRegisterPage'));
// Phase 1 screens 7 and 8. Guard-facing, but Omkar's — Phase 6 is the scanner
// and the verdicts, not the way in.
const GuardLoginPage = lazy(() => import('@features/auth/routes/GuardLoginPage'));
const GateSessionPage = lazy(() => import('@features/auth/routes/GateSessionPage'));

export const router = createBrowserRouter([
  { path: '/', element: <HomePage /> },
  { path: '/forbidden', element: <ForbiddenPage /> },

  {
    element: <PublicOnlyRoute />,
    children: [
      // Outside AuthLayout: the guard screen is dark and full-bleed, and the
      // light card wrapper would fight it.
      { path: '/guard/login', element: <GuardLoginPage /> },
      {
        element: <AuthLayout />,
        children: [
          { path: '/login', element: <LoginPage /> },
          { path: '/login/code', element: <EmailCodePage /> },
          { path: '/login/verify', element: <VerifyCodePage /> },
          { path: '/forgot-password', element: <ForgotPasswordPage /> },
          { path: '/reset-password', element: <ResetPasswordPage /> },
          { path: '/register/visitor', element: <VisitorRegisterPage /> },
        ],
      },
    ],
  },

  {
    element: <ProtectedRoute />,
    children: [
      // Sits OUTSIDE PasswordChangeGate: it is the one route a user holding a
      // temporary password is allowed to reach.
      {
        element: <AuthLayout />,
        children: [{ path: '/change-password', element: <ChangePasswordPage /> }],
      },

      {
        element: <PasswordChangeGate />,
        children: [
          /* ── PHASE 5 · Sanjay · src/features/visitor/ ────────────────── */
          {
            element: <RoleRoute roles={['VISITOR', 'STUDENT', 'FACULTY', 'CAMPUS_ADMIN', 'SUPER_ADMIN']} />,
            children: [
              {
                element: <AppShell />,
                children: [
                  {
                    path: '/visitor',
                    element: <PhasePending phase={5} owner="Sanjay" area="Visitor" screens={6} />,
                  },
                  /* ── PHASE 3 · Mukul · src/features/student/ ─────────── */
                  {
                    path: '/student',
                    element: <PhasePending phase={3} owner="Mukul" area="Student" screens={8} />,
                  },
                  /* ── PHASE 4 · Tushar · src/features/faculty/ ────────── */
                  {
                    path: '/faculty',
                    element: <PhasePending phase={4} owner="Tushar" area="Faculty" screens={10} />,
                  },
                ],
              },
            ],
          },

          /* ── PHASE 2 · Arham · src/features/campus-admin/ ─────────────── */
          {
            element: <RoleRoute roles={['CAMPUS_ADMIN']} />,
            children: [
              {
                element: <AppShell />,
                children: [
                  {
                    path: '/admin',
                    element: <PhasePending phase={2} owner="Arham" area="Campus Admin" screens={11} />,
                  },
                ],
              },
            ],
          },

          /* ── PHASE 2 · Arham · src/features/super-admin/ ──────────────── */
          {
            element: <RoleRoute roles={['SUPER_ADMIN']} />,
            children: [
              {
                element: <AppShell />,
                children: [
                  {
                    path: '/platform',
                    element: <PhasePending phase={2} owner="Arham" area="Platform" screens={5} />,
                  },
                ],
              },
            ],
          },

          /* ── PHASE 6 · Palash · src/features/guard/ ───────────────────
             GUARD only, no exceptions, not even an admin — an admin scanning
             would produce an entry log with no shift behind it. */
          {
            element: <RoleRoute roles={['GUARD']} />,
            children: [
              {
                element: <GuardSessionGate />,
                children: [
                  {
                    element: <GuardShell />,
                    children: [
                      {
                        path: '/guard',
                        element: <PhasePending phase={6} owner="Palash" area="Scanner" screens={8} />,
                      },
                      { path: '/guard/session', element: <GateSessionPage /> },
                    ],
                  },
                ],
              },
            ],
          },
        ],
      },
    ],
  },

  { path: '/404', element: <NotFoundPage /> },
  { path: '*', element: <Navigate to="/404" replace /> },
]);
