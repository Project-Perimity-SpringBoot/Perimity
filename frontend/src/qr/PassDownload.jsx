import { useState } from 'react';
import { Button, PassCard, LifecycleStrip } from '../shared/ui';
import { PASSES } from '../mock/data';

/**
 * Screens 12 and 13 — the pass a holder actually carries.
 *
 * THE QR IS THE LARGEST THING ON THE SCREEN. Not the header, not the branding.
 * This screen is opened one-handed at a gate with someone waiting behind you,
 * and everything else on it is secondary to a code a scanner can read.
 *
 * The PDF button is not decoration. Campus wi-fi at a gate is unreliable, so
 * an offline copy is the difference between entering and not entering.
 */
export default function PassDownload() {
  const [downloading, setDownloading] = useState(false);
  const pass = PASSES[0];

  return (
    <div className="p-stack" style={{ maxWidth: 460, margin: '0 auto' }}>
      <div>
        <h1 className="p-h1">Your pass</h1>
        <p className="p-caption">Show this at the gate. Screen brightness helps.</p>
      </div>

      <PassCard {...pass} qr variant="detail" />

      <LifecycleStrip status={pass.status} />

      <div className="p-stack">
        <Button block loading={downloading}
                onClick={() => { setDownloading(true); setTimeout(() => setDownloading(false), 900); }}>
          Download PDF
        </Button>
        <Button variant="secondary" block>Email it to me</Button>
      </div>

      <p className="p-caption">
        Keep the PDF on your phone. It opens without a network connection at the
        gate, and the code is the same one shown above.
      </p>

      {/* A paused pass still displays. The guard must be the one to refuse it,
          not the app — hiding the QR leaves the holder with nothing to show and
          no explanation of why. */}
      {pass.status === 'PAUSED' && (
        <div className="p-card p-pad-sm">
          <span className="p-label">Why is this paused?</span>
          <p className="p-caption" style={{ marginBottom: 0 }}>
            Your profile is incomplete. Passes resume automatically once it is
            complete — there is nothing to re-apply for.
          </p>
        </div>
      )}
    </div>
  );
}
