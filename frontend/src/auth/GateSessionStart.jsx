import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from '../shared/ui';
import { GATES } from '../mock/data';

/**
 * Screen 8 — bind the guard to ONE gate for the whole shift.
 *
 * The copy is doing real work here. FR-SESS-3 binds every scan in the session
 * to this gate, and the entry log is only evidence if the guard understood
 * that when they chose. So the consequence is stated before the button, not
 * in a tooltip after it.
 */
export default function GateSessionStart() {
  const [gate, setGate] = useState(null);
  const navigate = useNavigate();

  return (
    <div style={{ minHeight: '100vh', background: 'var(--ink-900)', display: 'grid', placeItems: 'center', padding: 'var(--s-4)' }}>
      <div className="p-card p-pad p-stack" style={{ width: '100%', maxWidth: 480 }}>
        <div>
          <h1 className="p-h1">Choose your gate</h1>
          <p className="p-body p-muted" style={{ margin: 0 }}>
            You will be bound to this gate for the whole shift. Every scan is
            logged against it.
          </p>
        </div>

        <div className="p-stack" style={{ gap: 'var(--s-2)' }}>
          {GATES.map((g) => (
            <button
              key={g.id} onClick={() => setGate(g)}
              className="p-card p-pad-sm"
              style={{
                textAlign: 'left', cursor: 'pointer', minHeight: 72,
                border: gate?.id === g.id ? '2px solid var(--brand-600)' : '1px solid var(--border)',
                background: gate?.id === g.id ? 'var(--brand-50)' : 'var(--surface)',
              }}
            >
              <div className="p-spread">
                <div>
                  <div className="p-h3">{g.name}</div>
                  <div className="p-caption">{g.location} · {g.type}</div>
                </div>
                <span className="p-mono p-muted">{g.id}</span>
              </div>
            </button>
          ))}
        </div>

        <Button size="lg" block disabled={!gate} onClick={() => navigate('/guard/scan')}>
          {gate ? `Start shift at ${gate.name}` : 'Select a gate'}
        </Button>
      </div>
    </div>
  );
}
