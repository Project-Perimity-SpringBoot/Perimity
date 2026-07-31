/**
 * Fixed to the top of EVERY guard screen, without exception.
 *
 * A guard is bound to one gate for a whole shift and every scan is logged
 * against it. If they are ever unsure which gate they are on, the entry log
 * stops being evidence. That is why this bar cannot be dismissed and is not
 * a screen-level decision.
 *
 * Gate name and ID are mono - the ID gets read aloud over a phone.
 */
export default function GuardSessionBar({ gate, gateId, guard, since, onEndShift }) {
  return (
    <div
      style={{
        position: 'sticky', top: 0, zIndex: 20,
        background: 'var(--ink-900)', color: 'var(--surface)',
        padding: 'var(--s-3) var(--s-4)',
        display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 'var(--s-3)',
      }}
    >
      <div className="p-row" style={{ gap: 'var(--s-2)', minWidth: 0 }}>
        <span className="p-mono">{gateId ?? gate}</span>
        <span aria-hidden style={{ opacity: .5 }}>·</span>
        <span className="p-small" style={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
          {guard}
        </span>
        <span aria-hidden style={{ opacity: .5 }}>·</span>
        <span className="p-mono" style={{ opacity: .8 }}>shift {since}</span>
      </div>

      <button
        onClick={onEndShift}
        className="p-btn"
        style={{
          minHeight: 36, padding: '0 var(--s-3)',
          background: 'transparent', color: 'var(--surface)',
          border: '1px solid rgba(255,255,255,.35)',
        }}
      >
        End shift
      </button>
    </div>
  );
}
