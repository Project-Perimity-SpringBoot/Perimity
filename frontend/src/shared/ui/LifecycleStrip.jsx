/**
 * PENDING → ACTIVE → PAUSED → EXPIRED / REVOKED, with the current state filled.
 *
 * Worth having because "paused" is not a word users expect on a pass, and a
 * strip showing where you are and what comes next answers "is this broken or
 * is this a step?" without a support message.
 *
 * Terminal states replace the tail rather than extending it: a REVOKED pass
 * never becomes anything else, and drawing EXPIRED after it would imply it
 * might.
 */
const FLOW = ['PENDING', 'ACTIVE', 'PAUSED'];

export default function LifecycleStrip({ status }) {
  const now = String(status || '').toUpperCase();
  const terminal = now === 'EXPIRED' || now === 'REVOKED';
  const steps = terminal ? ['PENDING', 'ACTIVE', now] : FLOW;
  const idx = steps.indexOf(now);

  return (
    <div className="p-life" role="list" aria-label="Pass lifecycle">
      {steps.map((s, i) => (
        <span key={s} style={{ display: 'contents' }}>
          {i > 0 && <span className="p-life__arrow" aria-hidden>→</span>}
          <span
            role="listitem"
            aria-current={s === now ? 'step' : undefined}
            className={[
              'p-life__step',
              s === now ? 'p-life__step--now' : i < idx ? 'p-life__step--done' : '',
            ].filter(Boolean).join(' ')}
          >
            {s.charAt(0) + s.slice(1).toLowerCase()}
          </span>
        </span>
      ))}
    </div>
  );
}
