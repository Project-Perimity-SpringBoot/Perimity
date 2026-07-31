import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider, useAuth } from './shared/AuthContext';
import { ToastProvider } from './shared/Toast';
import ProtectedRoute from './shared/ProtectedRoute';
import Layout from './shared/Layout';
import LoginPage from './auth/LoginPage';
import OtpVerifyPage from './auth/OtpVerifyPage';
import ChangePasswordPage from './auth/ChangePasswordPage';
import { featureRoutes, fullBleedRoutes, publicAuthRoutes } from './shared/featureRoutes';
import { HOME_FOR_ROLE } from './shared/roles';

/**
 * THIS FILE IS FINISHED. Nobody edits it to add a screen.
 *
 * Three tiers, and the middle one is the one people forget:
 *
 *   public       no token needed          /login, /otp, /guard/login
 *   full-bleed   signed in, NO shell      the guard app
 *   shell        signed in, with sidebar  everything else
 *
 * The guard tier exists because a scan verdict must fill the screen. Putting
 * it inside Layout would wrap a one-second, outdoors, glance-at-arm's-length
 * decision in a navigation sidebar.
 */
function RootRedirect() {
  const { user } = useAuth();
  return <Navigate to={HOME_FOR_ROLE[user.role] || '/student'} replace />;
}

export default function App() {
  return (
    <BrowserRouter>
      <ToastProvider>
        <AuthProvider>
          <Routes>
            {/* ---- public ---- */}
            <Route path="/login" element={<LoginPage />} />
            <Route path="/otp" element={<OtpVerifyPage />} />
            {publicAuthRoutes.map(({ path, element }) => (
              <Route key={path} path={path} element={element} />
            ))}

            <Route
              path="/change-password"
              element={<ProtectedRoute><ChangePasswordPage /></ProtectedRoute>}
            />

            {/* ---- signed in, no shell ---- */}
            {fullBleedRoutes.map(({ path, element, allow }) => (
              <Route
                key={path}
                path={path}
                element={<ProtectedRoute allow={allow}>{element}</ProtectedRoute>}
              />
            ))}

            {/* ---- signed in, inside the shell ---- */}
            <Route element={<ProtectedRoute><Layout /></ProtectedRoute>}>
              <Route index element={<RootRedirect />} />
              {featureRoutes.map(({ path, element, allow }) => (
                <Route
                  key={path}
                  path={path}
                  element={<ProtectedRoute allow={allow}>{element}</ProtectedRoute>}
                />
              ))}
            </Route>

            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </AuthProvider>
      </ToastProvider>
    </BrowserRouter>
  );
}
