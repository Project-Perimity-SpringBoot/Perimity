import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, ErrorState, CardGridSkeleton } from '../shared/ui';
import { campus, useApi } from '../api';
import { useAuth } from '../shared/AuthContext';
import { useGuardSession } from './useGuardSession';

/**
 * Gate selection at the start of a shift, and switching between shifts. LIVE.
 *
 * ONE GATE FOR THE WHOLE SHIFT. Every entry is logged against the session's
 * gate, so switching means ending the shift and starting another — not
 * changing a dropdown mid-scan. If a guard could switch silently, yesterday's
 * gate report would be wrong and nothing would ever reveal it.
 *
 * Gates are big tap targets, not a select. This is done with gloves on, in the
 * dark, in ten seconds, and it is the last screen before scanning starts.
 */
export default function GateSwitch() {
  const nav = useNavigate();
  const { user } = useAuth();
  const { session, start, end } = useGuardSession();
  const [busy, setBusy] = useState(null);
  const [error, setError] = useState(null);

  const { data: gates, loading, error: loadError, reload } =
    useApi(() => campus.gates(user.campusId), [user.campusId]);

  if (loading) return <CardGridSkeleton count={4} />;
  if (loadError) return <ErrorState title="Could not load gates"
                                    message={loadError.message} onRetry={reload} />;

  const active = (gates ?? []).filter((g) => g.active);

  const choose = async (gate) => {
    setBusy(gate.id); setError(null);
    try {
      if (session) await end();          // one gate per session, always
      await start(user.campusId, gate.id, gate.name);
      nav('/scan', { replace: true });
    } catch (e) {
      setError(e);
    } finally {
      setBusy(null);
    }
  };

  return (
    <div className="p-stack" style={{ maxWidth: 480, margin: '0 auto', padding: 'var(--s-4)' }}>
      <div>
        <h1 className="p-h1">{session ? 'Switch gate' : 'Start your shift'}</h1>
        <p className="p-caption">
          {session
            ? `You are on ${session.gateName}. Switching ends that shift and starts a new one.`
            : 'Every entry you record is logged against this gate.'}
        </p>
      </div>

      {error && <ErrorState title="Could not start the shift" message={error.message} />}

      <div className="p-stack">
        {active.map((g) => (
          <Button key={g.id} variant={session?.gateId === g.id ? 'secondary' : 'primary'}
                  size="lg" block loading={busy === g.id}
                  onClick={() => choose(g)}>
            {g.name}{g.location ? ` — ${g.location}` : ''}
            {session?.gateId === g.id ? ' (current)' : ''}
          </Button>
        ))}
      </div>

      {active.length === 0 && (
        <p className="p-caption">
          This campus has no active gates. A Campus Admin has to add one before
          anybody can scan.
        </p>
      )}
    </div>
  );
}
