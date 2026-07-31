import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../shared/AuthContext';
import { useToast } from '../shared/Toast';
import { HOME_FOR_ROLE } from '../shared/roles';

/**
 * Screen 1. Password login, with "use a one-time code instead".
 *
 * BOTH PATHS ARE ON ONE SCREEN, deliberately. Login is not the same for every
 * role - Super Admin, Campus Admin and Guard are password-only; Faculty and
 * Student may choose; a Visitor has no password at all - but the page CANNOT
 * ASK WHICH ROLE YOU ARE before you have logged in. It does not know, and a
 * role picker on a login page tells an outsider which addresses are admins.
 *
 * So: offer both, let the server decide. Ask for a code as a password-only
 * role and auth-service answers with the same generic message as always and
 * silently issues nothing.
 */
export default function LoginPage() {
  const { loginWithPassword, requestOtp } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const location = useLocation();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);

  const goHome = (user) => {
    const from = location.state?.from?.pathname;
    navigate(from || HOME_FOR_ROLE[user.role] || '/', { replace: true });
  };

  const submitPassword = async (e) => {
    e.preventDefault();
    setBusy(true);
    try {
      const auth = await loginWithPassword(email.trim(), password);
      if (auth.mustChangePassword) {
        navigate('/change-password', { replace: true });
        return;
      }
      goHome(auth.user);
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(false);
    }
  };

  const useOtpInstead = async () => {
    if (!email.trim()) {
      toast.error('Enter your email address first.');
      return;
    }
    setBusy(true);
    try {
      await requestOtp(email.trim());
      // The response is identical whether or not an account exists, so the
      // next screen is shown either way. That is the anti-enumeration design
      // working, not a bug to route around.
      toast.info('If that address is registered, a code has been sent.');
      navigate('/otp', { state: { email: email.trim() } });
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="centered">
      <form className="card" onSubmit={submitPassword}>
        <h1>Perimity</h1>
        <p className="muted">Sign in to continue</p>

        <label>
          Email
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="username"
            required
          />
        </label>

        <label>
          Password
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
          />
        </label>

        <button type="submit" disabled={busy}>
          {busy ? 'Signing in…' : 'Sign in'}
        </button>

        <button type="button" className="link" onClick={useOtpInstead} disabled={busy}>
          Use a one-time code instead
        </button>
      </form>
    </div>
  );
}
