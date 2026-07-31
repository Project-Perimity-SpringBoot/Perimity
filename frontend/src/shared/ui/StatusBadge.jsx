/**
 * The five pass states, and nothing else.
 *
 * DELIBERATELY COLOURLESS. Every badge is the same neutral grey and is
 * differentiated by its word. Two reasons, and the second is the real one:
 *
 *   1. Roughly 1 in 12 men cannot reliably separate red from green.
 *   2. Green and red are RESERVED for the guard's scan verdict. If ACTIVE is
 *      green on a table somewhere, then green stops meaning "let this person
 *      through" - which is the one place in the product where a colour has to
 *      carry weight on its own, outdoors, in under a second.
 *
 * PAUSED gets a glyph because it is the state people have never seen before
 * and the one that needs an explanation nearby.
 */
const GLYPH = { PAUSED: '⏸' };

const KNOWN = ['PENDING', 'ACTIVE', 'PAUSED', 'EXPIRED', 'REVOKED'];

export default function StatusBadge({ status, note }) {
  const s = String(status || '').toUpperCase();
  // "Upcoming" is not a status. A future-dated pass is PENDING with a note.
  const label = KNOWN.includes(s) ? s.charAt(0) + s.slice(1).toLowerCase() : s;

  return (
    <span className="p-badge" title={note || undefined}>
      {GLYPH[s] && <span aria-hidden>{GLYPH[s]}</span>}
      {label}
      {note && <span className="p-dim" style={{ fontWeight: 400 }}>· {note}</span>}
    </span>
  );
}
