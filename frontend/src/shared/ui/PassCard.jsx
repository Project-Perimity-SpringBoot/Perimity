import StatusBadge from './StatusBadge';

/**
 * ONE component for all three pass types. The ribbon is the only difference.
 *
 * Two variants: `compact` for lists and wallets, `detail` for the pass screen.
 * Building these as separate components is how they drift - the compact one
 * quietly stops showing the status six weeks later.
 *
 * The pass code is always mono. It is the thing a guard reads aloud over a
 * phone and types into manual lookup, so 0/O and 1/l have to be separable.
 */
export default function PassCard({
  type = 'daily',          // daily | event | visitor
  holder, code, campus, validity, status, note,
  eventName, qr, photo, variant = 'detail', signed = true, footer,
}) {
  const t = type.toLowerCase();
  const TYPE_LABEL = { daily: 'Daily pass', event: 'Event pass', visitor: 'Visitor pass' };

  return (
    <article className={`p-card p-pass p-pass--${t}`}>
      <div className="p-pass__ribbon" aria-hidden />
      <div className="p-pass__body">
        <div className="p-spread">
          <span className="p-pass__type">{TYPE_LABEL[t] ?? t}</span>
          <StatusBadge status={status} note={note} />
        </div>

        <div className="p-row">
          {photo && (
            <img
              src={photo} alt=""
              width={variant === 'compact' ? 36 : 48}
              height={variant === 'compact' ? 36 : 48}
              style={{ borderRadius: 'var(--r-circle)', objectFit: 'cover' }}
            />
          )}
          <div className="p-grow">
            <div className="p-h3">{holder}</div>
            {eventName && <div className="p-caption">{eventName}</div>}
          </div>
        </div>

        <div className="p-mono">{code}</div>

        {variant === 'detail' && (
          <>
            <div className="p-pass__qr">
              {qr ?? <span className="p-caption">QR</span>}
            </div>
            <dl className="p-pass__meta">
              <dt>Campus</dt><dd>{campus}</dd>
              <dt>Valid</dt><dd>{validity}</dd>
            </dl>
            {signed && (
              <span className="p-pass__signed">
                <span aria-hidden>🔒</span> Signed — verified by Perimity
              </span>
            )}
          </>
        )}

        {variant === 'compact' && <div className="p-caption">{validity}</div>}
        {footer}
      </div>
    </article>
  );
}
