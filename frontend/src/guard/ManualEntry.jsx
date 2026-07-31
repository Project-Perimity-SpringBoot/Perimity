import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, FormField, DataTable, EmptyState, GuardSessionBar,
         StatusBadge, ErrorState } from '../shared/ui';
import { gatepass } from '../api';
import { useGuardSession } from './useGuardSession';

/**
 * Manual lookup. LIVE.
 *
 * The camera fails often enough that this is a first-class screen: cracked
 * screens, dead batteries, glare, a browser without BarcodeDetector, a printed
 * pass that got wet. A guard with no way through just waves people in.
 *
 * IT DOES NOT LOG AN ENTRY. It shows a pass's status so the guard can decide,
 * and says so — a lookup silently writing an entry log would mean every
 * curious search became a recorded arrival, and the day's numbers would drift
 * without anybody knowing why.
 */
export default function ManualEntry() {
  const nav = useNavigate();
  const { session, end } = useGuardSession();
  const [passId, setPassId] = useState('');
  const [pass, setPass] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const look = async () => {
    setBusy(true); setError(null); setPass(null);
    try {
      setPass(await gatepass.pass(passId.trim()));
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div style={{ minHeight: '100vh', background: 'var(--surface-sunken)' }}>
      <GuardSessionBar
        gate={session?.gateName} gateId={session?.gateId} since={session?.startedAt}
        onEndShift={async () => { await end(); nav('/guard/ended'); }}
      />

      <div className="p-stack" style={{ maxWidth: 480, margin: '0 auto', padding: 'var(--s-4)' }}>
        <div>
          <h1 className="p-h1">Look up a pass</h1>
          <p className="p-caption">
            For when the code will not scan. This shows the pass — it does not
            record an entry.
          </p>
        </div>

        <div className="p-card p-pad">
          <div className="p-stack">
            <FormField label="Pass number" inputMode="numeric" value={passId}
                       onChange={(e) => setPassId(e.target.value)}
                       onKeyDown={(e) => e.key === 'Enter' && passId && look()}
                       help="Printed on the pass, under the QR code." />
            <Button block onClick={look} disabled={!passId.trim()} loading={busy}>
              Look up
            </Button>
          </div>
        </div>

        {error && <ErrorState title="No pass found"
                              message="Nothing matches that number at this campus. Check the digits." />}

        {pass && (
          <div className="p-card p-pad">
            <div className="p-stack">
              <div className="p-spread">
                <strong>{pass.holderName}</strong>
                <StatusBadge status={pass.status} note={pass.scannable ? undefined : 'will not scan'} />
              </div>
              <div className="p-caption">
                {pass.passType}{pass.eventName ? ` · ${pass.eventName}` : ''}
                <br />Valid {pass.validFrom} – {pass.validTo}
              </div>
              {pass.status === 'PAUSED' && pass.pausedReason && (
                <p className="p-caption" style={{ margin: 0 }}>Paused: {pass.pausedReason}</p>
              )}
              {pass.status === 'REVOKED' && pass.revokedReason && (
                <p className="p-caption" style={{ margin: 0 }}>Revoked: {pass.revokedReason}</p>
              )}
            </div>
          </div>
        )}

        <Button variant="secondary" block onClick={() => nav('/scan')}>Back to scanner</Button>
      </div>
    </div>
  );
}
