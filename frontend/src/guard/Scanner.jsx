import { useCallback, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, GuardSessionBar, ErrorState } from '../shared/ui';
import { guard } from '../api';
import { useScanner } from './useScanner';
import { useGuardSession } from './useGuardSession';

/**
 * The scanner. LIVE against guard-service.
 *
 * FULL-BLEED, no sidebar. Held at arm's length, outdoors, one-handed.
 *
 * The scan is fire-and-navigate: decode, POST, show the verdict. It does NOT
 * wait for a confirmation tap in between, because a queue at a gate is the
 * thing this screen is fighting, and the verdict screen itself is the
 * confirmation.
 *
 * The manual-lookup button is not a fallback tucked in a corner. Cracked
 * screens, dead phones, glare and browsers without BarcodeDetector are all
 * routine, and a guard who cannot find the other way in just waves people
 * through — which is worse than any UI compromise.
 */
export default function Scanner() {
  const nav = useNavigate();
  const { session, end } = useGuardSession();
  const [busy, setBusy] = useState(false);
  const [scanError, setScanError] = useState(null);

  const submit = useCallback(async (token) => {
    if (busy) return;
    setBusy(true);
    setScanError(null);
    try {
      const result = await guard.scan(token, { userAgent: navigator.userAgent.slice(0, 120) });
      // The whole response is handed to the verdict screen through router
      // state — it already contains the holder, the reason and the photo key,
      // so the verdict screen never re-fetches anything.
      nav('/scan/result', { state: { result } });
    } catch (e) {
      setScanError(e);
    } finally {
      setBusy(false);
    }
  }, [busy, nav]);

  const { videoRef, supported, scanning, error, start, stop } = useScanner(submit);

  return (
    <div style={{ minHeight: '100vh', background: 'var(--ink-900)', color: 'var(--surface)' }}>
      <GuardSessionBar
        gate={session?.gateName} gateId={session?.gateId}
        since={session?.startedAt}
        onEndShift={async () => { stop(); await end(); nav('/guard/ended'); }}
      />

      <div style={{ padding: 'var(--s-4)', display: 'grid', gap: 'var(--s-4)', placeItems: 'center' }}>
        <div style={{
          width: '100%', maxWidth: 420, aspectRatio: '1 / 1',
          background: 'var(--camera-bg)', borderRadius: 'var(--r-lg)', overflow: 'hidden',
          display: 'grid', placeItems: 'center', position: 'relative',
        }}>
          <video ref={videoRef} playsInline muted
                 style={{ width: '100%', height: '100%', objectFit: 'cover',
                          display: scanning ? 'block' : 'none' }} />
          {!scanning && (
            <span className="p-small" style={{ opacity: .7, textAlign: 'center', padding: 'var(--s-4)' }}>
              {supported
                ? 'Camera off. Start scanning when you are at the gate.'
                : 'This browser cannot decode QR codes. Use manual lookup.'}
            </span>
          )}
          {scanning && (
            // A frame, not a laser line. It tells the holder where to aim
            // without implying the app is doing something it is not.
            <div aria-hidden style={{
              position: 'absolute', inset: '18%', border: '3px solid var(--surface)',
              borderRadius: 'var(--r-md)', opacity: .8, pointerEvents: 'none',
            }} />
          )}
        </div>

        {(error || scanError) && (
          <div style={{ width: '100%', maxWidth: 420 }}>
            <ErrorState
              title={scanError ? 'Scan failed' : 'Camera unavailable'}
              message={(scanError || error).message}
              onRetry={scanError ? undefined : start}
            />
          </div>
        )}

        <div className="p-stack" style={{ width: '100%', maxWidth: 420 }}>
          {supported && (
            scanning
              ? <Button variant="secondary" block onClick={stop}>Pause camera</Button>
              : <Button block onClick={start} loading={busy}>Start scanning</Button>
          )}
          <Button variant="secondary" block onClick={() => nav('/guard/manual')}>
            Look up by name or code
          </Button>
        </div>

        {!session && (
          <p className="p-caption" style={{ color: 'var(--surface)', opacity: .8 }}>
            No shift started. Scans will be refused until you pick a gate.
          </p>
        )}
      </div>
    </div>
  );
}
