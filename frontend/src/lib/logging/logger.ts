/** Structured, level-gated logging. Nothing sensitive ever reaches it. */

type Level = 'debug' | 'info' | 'warn' | 'error';

const ORDER: Record<Level, number> = { debug: 10, info: 20, warn: 30, error: 40 };
const MIN: Level = import.meta.env.DEV ? 'debug' : 'warn';

/**
 * Allowlist, not denylist. Deny-listing fields is how the one you forgot ends
 * up in a log. Anything not named here is dropped.
 */
const SAFE_KEYS = new Set([
  'requestId', 'service', 'method', 'path', 'status', 'durationMs',
  'errorClass', 'retryAfterSeconds', 'count', 'batchId', 'passId', 'eventId',
]);

export function redact(payload: Record<string, unknown>): Record<string, unknown> {
  const safe: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(payload)) {
    if (SAFE_KEYS.has(key)) safe[key] = value;
  }
  return safe;
}

interface Entry {
  at: string;
  level: Level;
  message: string;
  context: Record<string, unknown>;
}

/** A bounded ring, ready for a sink that does not exist yet. */
const ring: Entry[] = [];
const RING_SIZE = 200;

function write(level: Level, message: string, context: Record<string, unknown> = {}): void {
  if (ORDER[level] < ORDER[MIN]) return;
  const entry: Entry = { at: new Date().toISOString(), level, message, context: redact(context) };
  ring.push(entry);
  if (ring.length > RING_SIZE) ring.shift();
  if (import.meta.env.DEV) {
    const fn = level === 'error' ? console.error : level === 'warn' ? console.warn : console.log;
    fn(`[${level}] ${message}`, entry.context);
  }
}

export const logger = {
  debug: (m: string, c?: Record<string, unknown>) => write('debug', m, c),
  info: (m: string, c?: Record<string, unknown>) => write('info', m, c),
  warn: (m: string, c?: Record<string, unknown>) => write('warn', m, c),
  error: (m: string, c?: Record<string, unknown>) => write('error', m, c),
  snapshot: (): readonly Entry[] => [...ring],
};
