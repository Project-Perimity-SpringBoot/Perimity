import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../shared/AuthContext';
import { useToast } from '../shared/Toast';
import { HOME_FOR_ROLE } from '../shared/roles';

/**
 * Screen 2. Enter the six-digit code.
 *
 * The countdown is the expiry auth-service already enforces (10 minutes), shown
 * so the user is not left guessing. Resend is disabled for 30 seconds because
 * the server rate-limits to three per email per hour - letting someone burn all
 * three in five seconds and then wait an hour is a worse experience than a
 * disabled button.
 */
const OTP_LENGTH = 6;
const EXPIRY_SECONDS = 10 * 60;
const RESEND_COOLDOWN = 30;

export default function OtpVerifyPage() {
  const { verifyOtp, requestOtp } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const email = location.state?.email;

  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [secondsLeft, setSecondsLeft] = useState(EXPIRY_SECONDS);
  const [cooldown, setCooldown] = useState(RESEND_COOLDOWN);

  // Someone who lands here directly has no email to verify against.
  useEffect(() => {
    if (!email) navigate('/login', { replace: true });
  }, [email, navigate]);

  useEffect(() => {
    const t = setInterval(() => {
      setSecondsLeft((s) => (s > 0 ? s - 1 : 0));
      setCooldown((c) => (c > 0 ? c - 1 : 0));
    }, 1000);
    return () => clearInterval(t);
  }, []);

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    try {
      const auth = await verifyOtp(email, code.trim());
      if (auth.mustChangePassword) {
        navigate('/change-password', { replace: true });
        return;
      }
      navigate(HOME_FOR_ROLE[auth.user.role] || '/', { replace: true });
    } catch (err) {
      // The server counts attempts and kills the code after five. It does not
      // tell us how many are left, and we do not guess - a wrong number here
      // would be worse than none.
      toast.error(err.message);
      setCode('');
    } finally {
      setBusy(false);
    }
  };

  const resend = async () => {
    try {
      await requestOtp(email);
      setSecondsLeft(EXPIRY_SECONDS);
      setCooldown(RESEND_COOLDOWN);
      toast.info('A new code has been sent. The previous one no longer works.');
    } catch (err) {
      toast.error(err.message);
    }
  };

  const mm = String(Math.floor(secondsLeft / 60)).padStart(2, '0');
  const ss = String(secondsLeft % 60).padStart(2, '0');

  return (
    <div className="centered">
      <form className="card" onSubmit={submit}>
        <h1>Check your email</h1>
        <p className="muted">We sent a {OTP_LENGTH}-digit code to {email}</p>

        <label>
          Code
          <input
            inputMode="numeric"
            pattern="\d*"
            maxLength={OTP_LENGTH}
            value={code}
            onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
            autoFocus
            required
          />
        </label>

        <p className="muted">
          {secondsLeft > 0 ? `Expires in ${mm}:${ss}` : 'This code has expired. Request a new one.'}
        </p>

        <button type="submit" disabled={busy || code.length !== OTP_LENGTH}>
          {busy ? 'Verifying…' : 'Verify'}
        </button>

        <button type="button" className="link" onClick={resend} disabled={cooldown > 0}>
          {cooldown > 0 ? `Resend in ${cooldown}s` : 'Send a new code'}
        </button>
      </form>
    </div>
  );
}
