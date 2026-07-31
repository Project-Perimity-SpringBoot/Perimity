import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../shared/AuthContext';
import { useToast } from '../shared/Toast';
import { HOME_FOR_ROLE } from '../shared/roles';

/**
 * FR-SESS-4. An account created by an administrator changes its password
 * before doing anything else.
 *
 * ProtectedRoute redirects here while user.mustChangePassword is true, so this
 * cannot be skipped by typing a different URL - and the flag is re-read from
 * the server after the change rather than assumed.
 */
export default function ChangePasswordPage() {
  const { user, changePassword } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();

  const [currentPassword, setCurrent] = useState('');
  const [newPassword, setNew] = useState('');
  const [confirmPassword, setConfirm] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    if (newPassword !== confirmPassword) {
      toast.error('The two new passwords do not match.');
      return;
    }
    setBusy(true);
    try {
      await changePassword(currentPassword, newPassword, confirmPassword);
      toast.success('Password changed.');
      navigate(HOME_FOR_ROLE[user.role] || '/', { replace: true });
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="centered">
      <form className="card" onSubmit={submit}>
        <h1>Choose a new password</h1>
        <p className="muted">
          {user.mustChangePassword
            ? 'This account was created for you. Set your own password to continue.'
            : 'Update your password.'}
        </p>

        <label>
          Current password
          <input type="password" value={currentPassword}
                 onChange={(e) => setCurrent(e.target.value)} required />
        </label>
        <label>
          New password
          <input type="password" value={newPassword}
                 onChange={(e) => setNew(e.target.value)} required />
        </label>
        <label>
          Confirm new password
          <input type="password" value={confirmPassword}
                 onChange={(e) => setConfirm(e.target.value)} required />
        </label>

        <p className="muted">
          At least 8 characters, with an uppercase letter, a lowercase letter
          and a digit.
        </p>

        <button type="submit" disabled={busy}>
          {busy ? 'Saving…' : 'Change password'}
        </button>
      </form>
    </div>
  );
}
