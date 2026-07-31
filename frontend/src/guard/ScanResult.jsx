import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Button, VerdictScreen } from '../shared/ui';
import { denialText, user } from '../api';

/**
 * The verdict. LIVE — it renders whatever guard-service returned.
 *
 * Backend ScanResult → what the guard sees:
 *   ALLOWED → ALLOW  (green)
 *   AMBER   → REVIEW (amber)  seen already today; the entry is still logged
 *   DENIED  → DENY   (red)    and the reason is ALWAYS shown
 *
 * "Denied" with no reason turns the guard into the argument rather than the
 * system. `denialText` maps every DenialReason to a sentence a guard can say
 * out loud, and falls back to a readable form of the enum for any reason added
 * backend-side after this was written — blank space next to a red screen is
 * the one outcome that must not happen.
 *
 * AUTO-RETURN. Ten seconds and it goes back to the camera. A verdict left on
 * screen is a verdict the next person in the queue sees, and it is somebody
 * else's name.
 */
const MAP = { ALLOWED: 'ALLOW', AMBER: 'REVIEW', DENIED: 'DENY' };
const RETURN_AFTER = 10;

export default function ScanResult() {
  const nav = useNavigate();
  const { state } = useLocation();
  const result = state?.result;
  const [left, setLeft] = useState(RETURN_AFTER);
  const [photo, setPhoto] = useState(null);

  useEffect(() => {
    if (!result) { nav('/scan', { replace: true }); return; }
    const t = setInterval(() => setLeft((n) => {
      if (n <= 1) { clearInterval(t); nav('/scan', { replace: true }); }
      return n - 1;
    }), 1000);
    return () => clearInterval(t);
  }, [result, nav]);

  /*
   * The holder's photo, so the guard can check the face against the person.
   * Fetched separately because the scan response carries a storage KEY, not a
   * URL — the bucket is private and links are short-lived.
   *
   * Deliberately non-blocking: the verdict renders immediately and the photo
   * appears when it appears. Making a red screen wait on an image request
   * would be the wrong trade at a gate.
   */
  useEffect(() => {
    let alive = true;
    if (!result?.holderUserId) return undefined;
    user.studentPhotoUrl(result.holderUserId)
      .then((r) => alive && setPhoto(typeof r === 'string' ? r : r?.url))
      .catch(() => {});
    return () => { alive = false; };
  }, [result]);

  if (!result) return null;

  const verdict = MAP[result.result] ?? 'REVIEW';

  return (
    <VerdictScreen
      verdict={verdict}
      holder={result.holderName}
      photo={photo}
      reason={result.result === 'DENIED' ? denialText(result.denialReason) : undefined}
      eventBanner={result.eventName}
      note={result.result === 'AMBER'
        ? 'Seen already today. This entry is still recorded.'
        : undefined}
      meta={[
        result.gateName,
        result.scannedAt ? new Date(result.scannedAt).toLocaleTimeString() : null,
        result.passId ? `Pass #${result.passId}` : null,
      ].filter(Boolean).join(' · ')}
      countdown={left}
      actions={
        <div className="p-stack">
          <Button block onClick={() => nav('/scan', { replace: true })}>Next person</Button>
          <Button variant="secondary" block onClick={() => nav('/guard/manual')}>
            Look up by name
          </Button>
        </div>
      }
    />
  );
}
