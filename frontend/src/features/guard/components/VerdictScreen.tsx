import { AlertTriangle, CalendarCheck, Check, X } from 'lucide-react';
import { Avatar, Button } from '@ui/index';
import { AuthedImage } from '@components/upload';
import { formatTime } from '@lib/format/datetime';
import type { DenialReason } from '@/types/enums';
import type { ScanResponse } from '@/types/guard.types';

/**
 * Phase 6 screens 2–5: ALLOW, ALLOW event-attributed, DENY, REVIEW.
 *
 * One component, four states. They are not four screens in the routing sense —
 * a verdict is the same moment with a different answer, and giving each its own
 * route would put a URL in front of something that lives for two seconds.
 *
 * ==========================================================================
 * ICON + WORD + COLOUR. NEVER COLOUR ALONE.
 * ==========================================================================
 * Roughly one man in twelve has some form of colour vision deficiency, and a
 * gate is exactly where that matters: bright sun, a cheap screen, two seconds
 * to decide. Every verdict therefore carries a glyph and a word that stand on
 * their own, and the colour is confirmation rather than information.
 *
 * This is also the ONLY place in the product where the verdict tokens are
 * allowed. tokens.css says so, and the reason is that using allow-green on a
 * dashboard stat trains the eye to stop reading green as "let them in".
 *
 * ==========================================================================
 * WHY THE SERVER'S MESSAGE IS RENDERED VERBATIM
 * ==========================================================================
 * `message` is composed server-side and is the sentence a guard says out loud.
 * Rebuilding it here from result + reason would mean two places deciding what
 * the gate says, and they would drift. The denial reason underneath is a
 * secondary line, not a replacement.
 */

/** Every DenialReason as something a guard can say to a person's face. */
const DENIAL_TEXT: Record<DenialReason, string> = {
  PASS_EXPIRED: 'This pass has expired',
  PASS_REVOKED: 'This pass was cancelled',
  PASS_PAUSED: 'This pass is paused while their details are re-checked',
  PASS_PENDING: 'This pass has not been activated yet',
  INVALID_TOKEN: 'This code is not a valid pass',
  WRONG_CAMPUS: 'This pass belongs to a different campus',
  WRONG_GATE: 'This pass is not valid at this gate',
  OUT_OF_DATE_RANGE: 'This pass is not valid today',
};

/**
 * A reason the server added after this build shipped still has to render as a
 * sentence rather than an enum name, so a guard is never shown WRONG_GATE.
 */
function denialText(reason: DenialReason | null): string | null {
  if (!reason) return null;
  return DENIAL_TEXT[reason] ?? reason.replaceAll('_', ' ').toLowerCase();
}

const VERDICT = {
  ALLOWED: {
    word: 'ENTER',
    Icon: Check,
    fg: 'var(--allow-fg)',
    bg: 'var(--allow-bg)',
    solid: 'var(--allow-solid)',
  },
  AMBER: {
    word: 'CHECK',
    Icon: AlertTriangle,
    fg: 'var(--review-fg)',
    bg: 'var(--review-bg)',
    solid: 'var(--review-solid)',
  },
  DENIED: {
    word: 'STOP',
    Icon: X,
    fg: 'var(--deny-fg)',
    bg: 'var(--deny-bg)',
    solid: 'var(--deny-solid)',
  },
} as const;

export function VerdictScreen({
  scan, onDismiss,
}: {
  scan: ScanResponse;
  onDismiss: () => void;
}) {
  const v = VERDICT[scan.result];

  const reason = denialText(scan.denialReason);

  return (
    <section
      role="alert"
      aria-label={`${v.word}. ${scan.message}`}
      className="flex min-h-[60dvh] flex-col items-center justify-center gap-[var(--sp-5)]
                 rounded-[var(--r-lg)] p-[var(--sp-6)] text-center"
      style={{ backgroundColor: v.bg }}
    >
      <div
        className="flex size-24 items-center justify-center rounded-full"
        style={{ backgroundColor: v.solid }}
      >
        <v.Icon aria-hidden className="size-14 text-white" strokeWidth={3} />
      </div>

      {/* The word. Readable across a gate, and meaningful without the colour. */}
      <p className="text-[2.5rem] font-bold leading-none tracking-wide" style={{ color: v.fg }}>
        {v.word}
      </p>

      {/* Screen 3: event-attributed. attributedEventId is set when Behavior 2
          credited this entry to a running event - including when the person
          scanned their ordinary daily pass. The guard is told nothing about
          that choice; it is bookkeeping, not a decision they make.

          The event NAME is not shown because ScanResponse.eventName is always
          null today - guard-service passes null explicitly. Rendering "Welcome
          to null" would be worse than rendering nothing, so the badge says only
          that it counted. */}
      {scan.result !== 'DENIED' && scan.attributedEventId !== null && (
        <p
          className="inline-flex items-center gap-[var(--sp-2)] rounded-full px-[var(--sp-4)]
                     py-[var(--sp-2)] text-body-md"
          style={{ backgroundColor: 'var(--surface)', color: v.fg }}
        >
          <CalendarCheck aria-hidden className="size-4" />
          Counted towards today&rsquo;s event
        </p>
      )}

      {scan.holderName && (
        <div className="flex flex-col items-center gap-[var(--sp-3)]">
          {/*
            THE PHOTO IS THE POINT OF THIS SCREEN.
            A valid QR proves the pass is real. It cannot prove the person
            holding the phone is the person it belongs to — a screenshot of
            somebody else's pass scans perfectly. The face is the only check
            that closes that, which is why FR-SCAN-9 asks for it.

            AuthedImage, NOT a plain <img>. This was a plain img and it never
            worked: in local-storage mode presignedReadUrl returns
            "/api/user/storage/local/{key}", a path back through user-service
            behind .authenticated(), and a browser sends no Authorization header
            with an image request. Every photo 401'd and fell back to initials —
            silently, which is why it looked deliberate rather than broken.

            AuthedImage fetches through the API client, so the interceptor adds
            the token, and it passes absolute S3 URLs straight through for when
            storage moves. Avatar stays as the fallback, so a visitor with no
            profile sees exactly what they saw before.
          */}
          <AuthedImage
            url={scan.holderPhotoUrl}
            alt={`Photo of ${scan.holderName}`}
            className="size-24 rounded-[var(--r-circle)] object-cover"
            fallback={<Avatar name={scan.holderName} className="size-16" />}
          />
          <p className="text-h2 text-[var(--ink-900)]">{scan.holderName}</p>
        </div>
      )}

      {/* Verbatim from the server. */}
      <p className="text-body-md max-w-[36ch] text-[var(--ink-700)]">{scan.message}</p>

      {reason && (
        <p className="text-body max-w-[36ch] font-medium" style={{ color: v.fg }}>
          {reason}
        </p>
      )}

      {scan.result === 'AMBER' && (
        <p className="text-small max-w-[38ch] text-[var(--ink-700)]">
          They have already entered today. Let them through if you are satisfied it is the
          same person — this is a note for the register, not a refusal.
        </p>
      )}

      <p className="text-caption text-[var(--ink-500)]">
        {scan.gateName} · {formatTime(scan.scannedAt)}
      </p>

      <Button size="lg" variant="secondary" block onClick={onDismiss}>
        Next person
      </Button>
    </section>
  );
}
