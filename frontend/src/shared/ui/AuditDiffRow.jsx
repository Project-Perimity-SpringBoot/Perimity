import { useState } from 'react';

/**
 * One audit line that expands into a before/after diff.
 *
 * Old value struck through in muted grey, new value in ink. Not red/green -
 * those are the guard's, and an audit entry is a record, not a judgement.
 *
 * Actor, IP, timestamp and user agent live in the expansion because the
 * collapsed row has to stay scannable: an audit log is read by someone looking
 * for one entry among thousands.
 */
export default function AuditDiffRow({ time, actor, action, target, changes = [], ip, agent }) {
  const [open, setOpen] = useState(false);
  const expandable = changes.length > 0 || ip || agent;

  return (
    <div style={{ borderBottom: '1px solid var(--border)' }}>
      <button
        onClick={() => expandable && setOpen(!open)}
        aria-expanded={expandable ? open : undefined}
        style={{
          width: '100%', display: 'flex', gap: 'var(--s-4)', alignItems: 'center',
          padding: 'var(--s-3) var(--s-4)', background: 'none', border: 0,
          cursor: expandable ? 'pointer' : 'default', textAlign: 'left', font: 'inherit',
        }}
      >
        <span className="p-mono" style={{ color: 'var(--ink-500)' }}>{time}</span>
        <span className="p-small" style={{ fontWeight: 600 }}>{actor}</span>
        <span className="p-small p-grow">{action} <span className="p-muted">{target}</span></span>
        {expandable && <span className="p-dim" aria-hidden>{open ? '▲' : '▼'}</span>}
      </button>

      {open && (
        <div style={{ padding: '0 var(--s-4) var(--s-4)' }}>
          {changes.map((c) => (
            <div key={c.field} style={{ marginBottom: 'var(--s-2)' }}>
              <div className="p-label" style={{ marginBottom: 'var(--s-1)' }}>{c.field}</div>
              <div className="p-diff">
                <div><div className="p-caption">Before</div><div className="p-diff__old">{c.from}</div></div>
                <div><div className="p-caption">After</div><div className="p-diff__new">{c.to}</div></div>
              </div>
            </div>
          ))}
          <div className="p-caption">
            {ip && <>IP <span className="p-mono">{ip}</span> · </>}
            {agent}
          </div>
        </div>
      )}
    </div>
  );
}
