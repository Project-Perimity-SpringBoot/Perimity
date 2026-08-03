import type { Role } from '@/types/enums';
import { ROLES } from '@/types/enums';

/**
 * JwtService.issue() produces exactly these claims. `sub` is a STRING — the
 * Java builder calls String.valueOf(user.getId()).
 */
export interface PerimityClaims {
  jti: string;
  sub: string;
  email: string;
  name: string;
  role: Role;
  campusId: number | null;
  iss: 'perimity-auth';
  iat: number;
  exp: number;
}

/** The identity the app actually uses, with sub already parsed. */
export interface Identity {
  userId: number;
  email: string;
  name: string;
  role: Role;
  /** null for SUPER_ADMIN. Its absence is what blocker B5 is about. */
  campusId: number | null;
  tokenId: string;
  expiresAt: Date;
}

function decodeSegment(segment: string): unknown {
  const padded = segment.replace(/-/g, '+').replace(/_/g, '/');
  const json = atob(padded.padEnd(padded.length + ((4 - (padded.length % 4)) % 4), '='));
  // decodeURIComponent/escape round-trip so non-ASCII names survive.
  return JSON.parse(
    decodeURIComponent(
      Array.from(json, (c) => `%${c.charCodeAt(0).toString(16).padStart(2, '0')}`).join(''),
    ),
  );
}

/**
 * Decode without verifying. Verification is the server's job — this is only
 * used to decide what to render and when to warn about expiry. A tampered
 * token still fails at every service.
 */
export function decodeClaims(token: string): PerimityClaims | null {
  const parts = token.split('.');
  if (parts.length !== 3 || !parts[1]) return null;

  let raw: unknown;
  try {
    raw = decodeSegment(parts[1]);
  } catch {
    return null;
  }
  if (typeof raw !== 'object' || raw === null) return null;

  const c = raw as Record<string, unknown>;
  if (
    typeof c['sub'] !== 'string' ||
    typeof c['email'] !== 'string' ||
    typeof c['role'] !== 'string' ||
    typeof c['exp'] !== 'number' ||
    c['iss'] !== 'perimity-auth'
  ) {
    return null;
  }
  if (!ROLES.includes(c['role'] as Role)) return null;

  const campus = c['campusId'];
  return {
    jti: typeof c['jti'] === 'string' ? c['jti'] : '',
    sub: c['sub'],
    email: c['email'],
    name: typeof c['name'] === 'string' ? c['name'] : c['email'],
    role: c['role'] as Role,
    campusId: typeof campus === 'number' ? campus : null,
    iss: 'perimity-auth',
    iat: typeof c['iat'] === 'number' ? c['iat'] : 0,
    exp: c['exp'],
  };
}

export function toIdentity(claims: PerimityClaims): Identity | null {
  const userId = Number(claims.sub);
  if (!Number.isFinite(userId)) return null;
  return {
    userId,
    email: claims.email,
    name: claims.name,
    role: claims.role,
    campusId: claims.campusId,
    tokenId: claims.jti,
    /*
     * A JWT `exp` is an RFC 7519 NumericDate — epoch SECONDS, a number, not a
     * server LocalDateTime string. Epoch millis carry no zone ambiguity, so
     * this is the correct construction and parseServerDateTime would be wrong
     * here: it parses a zone-less timestamp string, which is not what this is.
     */
    // eslint-disable-next-line no-restricted-syntax
    expiresAt: new Date(claims.exp * 1000),
  };
}

export function isExpired(claims: PerimityClaims, now: number = Date.now()): boolean {
  return claims.exp * 1000 <= now;
}

export function secondsUntilExpiry(claims: PerimityClaims, now: number = Date.now()): number {
  return Math.max(0, Math.floor((claims.exp * 1000 - now) / 1000));
}
