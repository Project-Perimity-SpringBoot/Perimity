import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, FormField } from '../shared/ui';
import { useAuth } from '../shared/AuthContext';
import { useToast } from '../shared/Toast';

/**
 * Screen 7 — guard sign-in. Deliberately NOT the same page as everyone else.
 *
 * Dark, oversized targets, one-handed. This is used at a gate, outdoors, on a
 * phone, by someone who may be wearing gloves. Password only — a guard has no
 * OTP option, because Role.canLoginWithOtp() excludes them and an OTP arriving
 * by email is useless to someone standing at a barrier.
 */
export default function GuardLogin() {
  const { loginWithPassword } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    try {
      await loginWithPassword(email.trim(), password);
      navigate('/guard/start-shift', { replace: true });
    } catch (err) {
      toast.error(err.message);
    } finally { setBusy(false); }
  };

  return (
    <div style={{ minHeight: '100vh', background: 'var(--ink-900)', display: 'grid', placeItems: 'center', padding: 'var(--s-4)' }}>
      <form onSubmit={submit} className="p-card p-pad p-stack" style={{ width: '100%', maxWidth: 420 }}>
        <div>
          <span className="p-label">Perimity</span>
          <h1 className="p-h1">Guard sign in</h1>
          <p className="p-caption">You will choose your gate on the next screen.</p>
        </div>
        <FormField label="Email" type="email" value={email} required
                   onChange={(e) => setEmail(e.target.value)} autoComplete="username" />
        <FormField label="Password" type="password" value={password} required
                   onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" />
        <Button type="submit" size="lg" block loading={busy}>Sign in</Button>
      </form>
    </div>
  );
}
