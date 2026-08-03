/** Typed environment access. Nothing reads import.meta.env directly. */

function str(key: string, fallback: string): string {
  const value = import.meta.env[key];
  return typeof value === 'string' && value.length > 0 ? value : fallback;
}

function num(key: string, fallback: number): number {
  const parsed = Number(import.meta.env[key]);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function bool(key: string, fallback: boolean): boolean {
  const value = import.meta.env[key];
  if (value === 'true') return true;
  if (value === 'false') return false;
  return fallback;
}

export const config = {
  apiMode: str('VITE_API_MODE', 'proxy') as 'proxy' | 'direct',
  campusTimezone: str('VITE_CAMPUS_TIMEZONE', 'Asia/Kolkata'),
  idleTimeoutMinutes: num('VITE_IDLE_TIMEOUT_MINUTES', 30),
  expiryWarningMinutes: num('VITE_EXPIRY_WARNING_MINUTES', 5),
  guardExpiryWarningMinutes: num('VITE_GUARD_EXPIRY_WARNING_MINUTES', 15),
  /** B12 — GET /campuses needs a token a first-time visitor does not have. */
  defaultCampusId: num('VITE_DEFAULT_CAMPUS_ID', 1),
} as const;

/**
 * Each flag names the blocker that forced it. A flag with no expiry condition
 * becomes permanent debt, so every one is deleted the day its blocker clears.
 */
export const flags = {
  /** B1 — nothing in qr-service serves the QR PNG or the pass PDF. */
  passDownload: bool('VITE_ENABLE_PASS_DOWNLOAD', false),
  /** B9 — a STUDENT gets 403 on /api/guard/entry-logs/**. */
  studentEntryHistory: bool('VITE_ENABLE_STUDENT_ENTRY_HISTORY', false),
  /** B8 — no manual lookup and no override endpoint exist. */
  guardManualLookup: bool('VITE_ENABLE_GUARD_MANUAL_LOOKUP', false),
  /** B10 — BlocklistController is SA/CA only, so faculty get 403. */
  blocklistCheckLine: bool('VITE_ENABLE_BLOCKLIST_CHECK_LINE', false),
} as const;

export type FeatureFlag = keyof typeof flags;
