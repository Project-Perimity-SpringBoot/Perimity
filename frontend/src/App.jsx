import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider, useAuth } from './shared/AuthContext';
import { ToastProvider } from './shared/Toast';
import ProtectedRoute from './shared/ProtectedRoute';
import Layout from './shared/Layout';
import LoginPage from './auth/LoginPage';
import OtpVerifyPage from './auth/OtpVerifyPage';
import ChangePasswordPage from './auth/ChangePasswordPage';
import Placeholder from './pages/Placeholder';
import { HOME_FOR_ROLE, ROLES } from './shared/roles';

/**
 * THE ROUTE TABLE. This is the file teammates edit on Day 13.
 *
 * Add one <Route> for your screen, wrapped in <ProtectedRoute allow={[...]}>,
 * and add a matching line to NAV in shared/Layout.jsx. Nothing else in the
 * shell needs touching, which is the point of it existing.
 */
function RootRedirect() {
  const { user } = useAuth();
  return <Navigate to={HOME_FOR_ROLE[user.role] || '/my-pass'} replace />;
}

export default function App() {
  return (
    <BrowserRouter>
      <ToastProvider>
        <AuthProvider>
          <Routes>
            {/* Public */}
            <Route path="/login" element={<LoginPage />} />
            <Route path="/otp" element={<OtpVerifyPage />} />

            {/* Signed in, but outside the layout - nothing else is reachable
                until the password is changed, so a sidebar would be a lie. */}
            <Route
              path="/change-password"
              element={
                <ProtectedRoute>
                  <ChangePasswordPage />
                </ProtectedRoute>
              }
            />

            {/* Everything inside the shell */}
            <Route
              element={
                <ProtectedRoute>
                  <Layout />
                </ProtectedRoute>
              }
            >
              <Route index element={<RootRedirect />} />

              <Route
                path="/my-pass"
                element={
                  <ProtectedRoute allow={[ROLES.STUDENT, ROLES.VISITOR, ROLES.FACULTY]}>
                    <Placeholder title="My Pass" owner="Tushar" screens="8" />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/approvals"
                element={
                  <ProtectedRoute allow={[ROLES.FACULTY, ROLES.CAMPUS_ADMIN]}>
                    <Placeholder title="Approval Queue" owner="Tushar" screens="7" />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/scan"
                element={
                  <ProtectedRoute allow={[ROLES.GUARD]}>
                    <Placeholder title="Guard Scanner" owner="Palash" screens="13, 15" />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/campus"
                element={
                  <ProtectedRoute allow={[ROLES.CAMPUS_ADMIN]}>
                    <Placeholder title="Campus Admin" owner="Arham" screens="16, 19" />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/admin"
                element={
                  <ProtectedRoute allow={[ROLES.SUPER_ADMIN]}>
                    <Placeholder title="Super Admin Console" owner="Sanjay" screens="20" />
                  </ProtectedRoute>
                }
              />
            </Route>

            {/* Unknown URL - send them somewhere real rather than a blank page */}
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </AuthProvider>
      </ToastProvider>
    </BrowserRouter>
  );
}
