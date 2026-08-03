import type { PerimityClaims, Identity } from './claims';
import { decodeClaims, isExpired, toIdentity } from './claims';
import { NotSupportedError } from '@lib/api/errors';

/**
 * The ONLY module in the codebase that knows a token exists.
 *
 * Storage today: in memory, mirrored to sessionStorage so a reload survives.
 * Not localStorage — a 24-hour token with no refresh, no rotation and no
 * cross-service denylist, sitting in a store any XSS can read, is the worst
 * available combination. sessionStorage at least dies with the tab.
 *
 * Migrating to HttpOnly cookies later means rewriting this file and deleting
 * one interceptor line. No feature file changes.
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
    const raw = sessionStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as Persisted) : null;
  } catch {
    return null;
  }
}

function hydrate(): void {
  const persisted = readPersisted();
  if (!persisted) return;
  const claims = decodeClaims(persisted.token);
  if (!claims || isExpired(claims)) {
    try {
      sessionStorage.removeItem(STORAGE_KEY);
    } catch {
      /* private mode */
    }
    return;
  }
  memoryToken = persisted.token;
  memoryClaims = claims;
}

hydrate();

channel?.addEventListener('message', (event: MessageEvent<{ type: string }>) => {
  if (event.data?.type === 'logout') {
    memoryToken = null;
    memoryClaims = null;
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
    // eslint-disable-next-line no-restricted-syntax
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
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ token, expiresAt } satisfies Persisted));
    } catch {
      // Private mode. The session still works; it just will not survive reload.
    }
    notify();
  },

  clear(broadcast = true): void {
    memoryToken = null;
    memoryClaims = null;
    try {
      sessionStorage.removeItem(STORAGE_KEY);
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
