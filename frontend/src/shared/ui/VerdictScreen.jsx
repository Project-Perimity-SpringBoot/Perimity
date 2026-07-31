/**
 * The guard's scan result. The only place in Perimity that uses green, red
 * and amber, and the screen the whole product is judged on.
 *
 * EVERY VERDICT COMBINES AN ICON, A WORD AND A COLOUR. Never colour alone.
 * This is read outdoors, in sunlight, in under a second, by someone who may be
 * colour-blind and is holding a phone in one hand. Any one of the three
 * failing must still leave the answer legible.
 *
 * Full-bleed on purpose: the colour has to be readable at arm's length from a
 * tablet on a desk, not just by the person holding it.
 *
 * A denial ALWAYS states a reason. "Denied" with no reason turns the guard
 * into the argument rather than the system.
 */
const V = {
  ALLOW:  { icon: '✓', word: 'ALLOW',  fg: 'var(--allow-fg)',  bg: 'var(--allow-bg)',  solid: 'var(--allow-solid)' },
  DENY:   { icon: '✕', word: 'DENY',   fg: 'var(--deny-fg)',   bg: 'var(--deny-bg)',   solid: 'var(--deny-solid)' },
  REVIEW: { icon: '!', word: 'REVIEW', fg: 'var(--review-fg)', bg: 'var(--review-bg)', solid: 'var(--review-solid)' },
};

export default function VerdictScreen({
  verdict = 'ALLOW', reason, holder, meta, photo, eventBanner, note, countdown, actions,
}) {
  const v = V[String(verdict).toUpperCase()] ?? V.REVIEW;

  return (
    <section
      role="status" aria-live="assertive"
      style={{
        minHeight: '100vh', background: v.bg, color: v.fg,
        display: 'flex', flexDirection: 'column', alignItems: 'center',
        justifyContent: 'center', gap: 'var(--s-6)', padding: 'var(--s-6)', textAlign: 'center',
      }}
    >
      <div
        aria-hidden
        style={{
          width: 96, height: 96, borderRadius: 'var(--r-circle)', background: v.solid,
          color: 'var(--surface)', display: 'grid', placeItems: 'center', fontSize: 52, lineHeight: 1,
        }}
      >
        {v.icon}
      </div>

      <h1 style={{
        margin: 0, fontSize: 44, fontWeight: 800,
        letterSpacing: '0.04em', color: v.fg,
      }}>
        {v.word}
      </h1>

      {reason && <p className="p-h2" style={{ margin: 0, color: v.fg }}>{reason}</p>}

      {eventBanner && (
        <div style={{
          background: v.solid, color: 'var(--surface)',
          padding: 'var(--s-3) var(--s-6)', borderRadius: 'var(--r-pill)',
          fontSize: 'var(--t-h3-size)', fontWeight: 700,
        }}>
          {eventBanner}
        </div>
      )}

      {(holder || photo) && (
        <div className="p-card p-pad" style={{ width: '100%', maxWidth: 420, color: 'var(--ink-900)' }}>
          <div className="p-row">
            {photo && <img src={photo} alt="" width={64} height={64}
                           style={{ borderRadius: 'var(--r-circle)', objectFit: 'cover' }} />}
            <div className="p-grow" style={{ textAlign: 'left' }}>
              <div className="p-h3">{holder}</div>
              {meta && <div className="p-caption">{meta}</div>}
            </div>
          </div>
        </div>
      )}

      {/* Behavior 2 made visible: the guard sees one green light, the note
          explains the attribution without asking them to do anything. */}
      {note && <p className="p-small" style={{ margin: 0, color: v.fg, opacity: .85 }}>{note}</p>}

      {actions && <div className="p-row">{actions}</div>}

      {countdown != null && (
        <span className="p-caption" style={{ color: v.fg }}>
          Returns to scanner in {countdown}s
        </span>
      )}
    </section>
  );
}
