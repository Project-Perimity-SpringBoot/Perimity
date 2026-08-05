import type { PerimityClaims, Identity } from './claims';
import { decodeClaims, isExpired, toIdentity } from './claims';
import { NotSupportedError } from '@lib/api/errors';

/**
 * The ONLY module in the codebase that knows a token exists.
 *
 * ==========================================================================
 * STORAGE: IN MEMORY, MIRRORED TO localStorage
 * ==========================================================================
 * A session follows the person across every tab in the browser, and survives
 * closing the browser until the token expires on its own. This is what people
 * expect, because it is what every site they already use does.
 *
 * This was sessionStorage until now, and the comment here argued for it: a
 * 24-hour token with no refresh, no rotation and no cross-service denylist,
 * sitting in a store any XSS can read, is a poor combination. That argument is
 * still true. What changed is the honest assessment of the other side of it.
 *
 * WHAT localStorage ACTUALLY COSTS US
 *
 * The threat is script execution on our own origin, which would read the token
 * and use it for up to 24 hours with no way to revoke it. So the question is
 * how script gets to run here at all:
 *
 *   - There is no dangerouslySetInnerHTML, no innerHTML assignment and no
 *     eval() anywhere in src/. React escapes interpolated values by default,
 *     so there is no HTML injection sink in application code.
 *   - The one real sink was stored content: user-uploaded files served back
 *     from our own origin. That is already closed - UploadValidator refuses
 *     SVG outright (it is XML and can carry script) and checks leading bytes
 *     rather than trusting the declared type, and LocalStorageController
 *     serves everything with X-Content-Type-Options: nosniff and
 *     Content-Disposition: inline.
 *   - What remains is a compromised dependency. That is a real risk, and
 *     sessionStorage does not protect against it either: a malicious package
 *     runs inside the tab that is already signed in, and can read memory, make
 *     requests with the live token, or simply wait.
 *
 * So sessionStorage was buying less than it appeared to. It reliably stopped
 * one thing - a token surviving a closed tab - and charged for it by making
 * every deep link, every bookmark and every second tab a fresh sign-in.
 *
 * WHAT WOULD ACTUALLY BE BETTER, AND WHY IT IS NOT HERE
 *
 * An HttpOnly, Secure, SameSite cookie holding a short-lived access token,
 * with a rotating refresh token. Script cannot read it at all. That is what a
 * large site does, and it is the right destination.
 *
 * It is not a frontend change. auth-service would have to set the cookie, all
 * six services would have to accept it, CSRF protection becomes necessary the
 * moment credentials travel automatically, and the CORS configuration changes
 * everywhere. That is a cross-team change to five services this file's author
 * does not own.
 *
 * When it happens it is this file plus one interceptor line. No feature file
 * changes - which is the whole reason the rest of the codebase is not allowed
 * to know a token exists.
 *
 * A BroadcastChannel handoff - a new tab asking open tabs for the session, so
 * nothing is ever persisted - was considered and rejected. Script running on
 * this origin could send the same message and be handed the token, so it moves
 * the risk rather than removing it, and it signs the person out whenever the
 * last tab closes.
 *
 * ==========================================================================
 * KEEPING TABS IN AGREEMENT
 * ==========================================================================
 * One storage means one session, so two tabs must never disagree about who is
 * signed in. The `storage` event fires in every OTHER tab when localStorage
 * changes, which covers sign-in, sign-out and expiry-clearing in a single
 * handler without anything having to announce itself.
 *
 * The BroadcastChannel is kept for the one case that event cannot cover: a
 * browser in private mode where the write throws, so nothing changes in
 * storage and no event is raised.
 */

const STORAGE_KEY = 'perimity.session';
const CHANNEL = 'perimity-auth';

interface Persisted {
  token: string;
  expiresAt: string;
}

let memoryToken: string | null = null;
let memoryClaims: PerimityClaims | null = null;

const listeners = new Set<() => void>();
const channel: BroadcastChannel | null =
  typeof BroadcastChannel !== 'undefined' ? new BroadcastChannel(CHANNEL) : null;

function notify(): void {
  for (const listener of listeners) listener();
}

function readPersisted(): Persisted | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as Persisted) : null;
  } catch {
    return null;
  }
}

function forget(): void {
  memoryToken = null;
  memoryClaims = null;
}

/**
 * Read storage into memory.
 *
 * An expired token is removed rather than left to be rejected later. Without
 * that, opening the app the morning after would show the shell of a signed-in
 * session and then 401 on its first request.
 */
function hydrate(): void {
  forget();
  const persisted = readPersisted();
  if (!persisted) return;

  const claims = decodeClaims(persisted.token);
  if (!claims || isExpired(claims)) {
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      /* private mode */
    }
    return;
  }

  memoryToken = persisted.token;
  memoryClaims = claims;
}

hydrate();

/*
 * Fired in every OTHER tab when this key changes - sign-in, sign-out and
 * expiry-clearing all arrive here.
 *
 * event.key is null when the whole store is cleared at once, which has to be
 * treated as a change rather than ignored.
 */
if (typeof window !== 'undefined') {
  window.addEventListener('storage', (event: StorageEvent) => {
    if (event.key !== null && event.key !== STORAGE_KEY) return;
    hydrate();
    notify();
  });
}

channel?.addEventListener('message', (event: MessageEvent<{ type: string }>) => {
  if (event.data?.type === 'logout') {
    forget();
    notify();
  }
});

export const tokenStore = {
  get(): string | null {
    return memoryToken;
  },

  claims(): PerimityClaims | null {
    return memoryClaims;
  },

  identity(): Identity | null {
    return memoryClaims ? toIdentity(memoryClaims) : null;
  },

  expiresAt(): Date | null {
    // Epoch seconds from the JWT, not a server timestamp string — see the note
    // on the same construction in claims.ts.
    return memoryClaims ? new Date(memoryClaims.exp * 1000) : null;
  },

  isAuthenticated(): boolean {
    return memoryClaims !== null && !isExpired(memoryClaims);
  },

  set(token: string, expiresAt: string): void {
    const claims = decodeClaims(token);
    if (!claims) throw new Error('Refused to store a token whose claims could not be read');
    memoryToken = token;
    memoryClaims = claims;
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({ token, expiresAt } satisfies Persisted));
    } catch {
      // Private mode. The session still works in this tab; it just will not
      // survive a reload and will not reach any other tab.
    }
    notify();
  },

  clear(broadcast = true): void {
    forget();
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      /* private mode */
    }
    if (broadcast) channel?.postMessage({ type: 'logout' });
    notify();
  },

  subscribe(listener: () => void): () => void {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },

  /**
   * There is no refresh endpoint in the Perimity backend — no refresh token,
   * no rotation, 24-hour absolute expiry. This exists so every call site
   * already handles the rejection, making a future refresh a one-file change.
   */
  async refresh(): Promise<void> {
    throw new NotSupportedError(
      'This backend issues no refresh token. A session ends at expiry and requires a new sign-in.',
    );
  },
};
