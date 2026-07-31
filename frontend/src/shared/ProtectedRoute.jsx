import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from './AuthContext';

/**
 * Route-level authorisation.
 *
 * THIS IS CONVENIENCE, NOT SECURITY. Anyone can edit the JavaScript in their
 * own browser and render any screen they like. What actually stops them is
 * @PreAuthorize and CurrentUser.requireSameCampus on the server - this only
 * keeps honest users out of screens that would fail for them anyway.
 *
 * Worth saying plainly in the viva, because "we secured it in React" is the
 * wrong answer and someone will ask.
 *
 * Usage:
 *   <ProtectedRoute allow={[ROLES.CAMPUS_ADMIN]}><CampusSettings/></ProtectedRoute>
 */
export default function ProtectedRoute({ allow, children }) {
  const { user, loading, isAuthenticated } = useAuth();
  const location = useLocation();

  // Do not decide anything while GET /me is still in flight, or a refresh on
  // a protected page bounces the user to login before the session is restored.
  if (loading) return <div className="centered">Loading…</div>;

  if (!isAuthenticated) {
    // Remember where they were headed so login can send them back.
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  // FR-SESS-4: an admin-created account changes its password before doing
  // anything else. Enforced here so no individual page has to remember.
  if (user.mustChangePassword && location.pathname !== '/change-password') {
    return <Navigate to="/change-password" replace />;
  }

  if (allow && !allow.includes(user.role)) {
    return (
      <div className="centered">
        <h2>Not available for your role</h2>
        <p>
          You are signed in as {user.role}. This screen is for {allow.join(', ')}.
        </p>
      </div>
    );
  }

  return children;
}
