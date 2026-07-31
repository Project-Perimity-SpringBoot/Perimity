import { useNavigate } from 'react-router-dom';
import { Button } from '../shared/ui';

/**
 * Screen 9 — two states on one screen: locked out, and session expired.
 *
 * They share a layout because they share a feeling ("I cannot get in") but the
 * copy must differ: one is temporary and self-resolving, the other needs an
 * administrator. Saying "try again later" to a locked account wastes fourteen
 * minutes of someone's shift.
 */
export default function SessionEnded({ mode = 'expired', unlockIn = '14:32' }) {
  const navigate = useNavigate();
  const locked = mode === 'locked';

  return (
    <div className="centered" style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', padding: 'var(--s-4)' }}>
      <div className="p-card p-pad p-stack" style={{ maxWidth: 420, textAlign: 'center' }}>
        <span className="p-empty__icon" aria-hidden>{locked ? '⏳' : '○'}</span>
        <h1 className="p-h2">{locked ? 'Account temporarily locked' : 'Your session ended'}</h1>
        <p className="p-body p-muted" style={{ margin: 0 }}>
          {locked
            ? <>Too many failed attempts. Try again in <span className="p-mono">{unlockIn}</span>, or contact your campus administrator.</>
            : 'For your security you were signed out. Sign in again to continue.'}
        </p>
        <Button block onClick={() => navigate('/login')}>Back to sign in</Button>
      </div>
    </div>
  );
}
