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

/* ── PHASE 6 · Guard ───────────────────────────────────────────────────── */
const ScannerPage = lazy(() => import('@features/guard/routes/ScannerPage'));
const GuardShiftEndPage = lazy(() => import('@features/guard/routes/ShiftEndPage'));
const GuardManualEntryPage = lazy(() => import('@features/guard/routes/ManualEntryPage'));

/* ── PHASE 2 · Campus Admin ── */
const AdminOverview = lazy(() => import('@features/campus-admin/routes/AdminOverview'));
const VisitorQueuePage = lazy(() => import('@features/campus-admin/routes/VisitorQueuePage'));
const UsersPage = lazy(() => import('@features/campus-admin/routes/UsersPage'));
const DepartmentsPage = lazy(() => import('@features/campus-admin/routes/DepartmentsPage'));
const GatesPage = lazy(() => import('@features/campus-admin/routes/GatesPage'));
const BlocklistPage = lazy(() => import('@features/campus-admin/routes/BlocklistPage'));
const PolicyPage = lazy(() => import('@features/campus-admin/routes/PolicyPage'));
const EntryLogsPage = lazy(() => import('@features/campus-admin/routes/EntryLogsPage'));

/* ── PHASE 2 · Super Admin ── */
/* ── PHASE 3 · Student ─────────────────────────────────────────────────── */
const StudentDashboard = lazy(() => import('@features/student/routes/StudentDashboard'));
const StudentPassHistory = lazy(() => import('@features/student/routes/PassHistoryPage'));
const StudentPassDetail = lazy(() => import('@features/student/routes/PassDetailPage'));
const StudentEntryHistory = lazy(() => import('@features/student/routes/EntryHistoryPage'));
const StudentProfile = lazy(() => import('@features/student/routes/ProfilePage'));
const StudentProfileEdit = lazy(() => import('@features/student/routes/ProfileEditPage'));
const StudentDocuments = lazy(() => import('@features/student/routes/DocumentsPage'));

const PlatformOverview = lazy(() => import('@features/super-admin/routes/PlatformOverview'));
const CampusesPage = lazy(() => import('@features/super-admin/routes/CampusesPage'));
const CampusAdminsPage = lazy(() => import('@features/super-admin/routes/CampusAdminsPage'));

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
                  /* ── PHASE 3 · Mukul · src/features/student/ · LANDED ──
                     Paths match layouts/navigation.ts exactly. /student/passes
                     is the history list and /student/passes/:id its detail, so
                     the sidebar item stays highlighted on both. */
                  { path: '/student', element: <StudentDashboard /> },
                  { path: '/student/passes', element: <StudentPassHistory /> },
                  { path: '/student/passes/:id', element: <StudentPassDetail /> },
                  { path: '/student/entries', element: <StudentEntryHistory /> },
                  { path: '/student/profile', element: <StudentProfile /> },
                  { path: '/student/profile/edit', element: <StudentProfileEdit /> },
                  { path: '/student/documents', element: <StudentDocuments /> },
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
                  { path: '/admin', element: <AdminOverview /> },
                  { path: '/admin/queue', element: <VisitorQueuePage /> },
                  { path: '/admin/users', element: <UsersPage /> },
                  { path: '/admin/departments', element: <DepartmentsPage /> },
                  { path: '/admin/gates', element: <GatesPage /> },
                  { path: '/admin/blocklist', element: <BlocklistPage /> },
                  { path: '/admin/policy', element: <PolicyPage /> },
                  { path: '/admin/entry-logs', element: <EntryLogsPage /> },
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
                  { path: '/platform', element: <PlatformOverview /> },
                  { path: '/platform/campuses', element: <CampusesPage /> },
                  { path: '/platform/admins', element: <CampusAdminsPage /> },
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
                      /* The scanner IS the guard's home screen. There is no
                         guard dashboard, deliberately — nothing competes with
                         the viewfinder. */
                      { path: '/guard', element: <ScannerPage /> },
                      { path: '/guard/session', element: <GateSessionPage /> },
                      { path: '/guard/shift-end', element: <GuardShiftEndPage /> },
                      /* Behind VITE_ENABLE_GUARD_MANUAL_LOOKUP (B8). The route
                         exists either way so the link never 404s; the screen
                         explains itself when the endpoints are missing. */
                      { path: '/guard/manual', element: <GuardManualEntryPage /> },
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
