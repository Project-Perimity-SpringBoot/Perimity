import dayjs from 'dayjs';
import utc from 'dayjs/plugin/utc';
import timezone from 'dayjs/plugin/timezone';
import customParseFormat from 'dayjs/plugin/customParseFormat';

dayjs.extend(utc);
dayjs.extend(timezone);
dayjs.extend(customParseFormat);

/**
 * Every server timestamp is a LocalDateTime with no zone and no offset, so
 * `new Date("2026-08-01T09:38:00")` is parsed as BROWSER-local — right only if
 * the browser happens to sit in the campus timezone.
 *
 * Treat all server timestamps as campus-local wall time. This is the only
 * permitted entry point; ESLint bans `new Date(` on API values.
 */
export const CAMPUS_TZ: string =
  (import.meta.env['VITE_CAMPUS_TIMEZONE'] as string | undefined) ?? 'Asia/Kolkata';

const DATE_TIME = 'YYYY-MM-DDTHH:mm:ss';
const DATE = 'YYYY-MM-DD';

export function parseServerDateTime(value: string | null | undefined): dayjs.Dayjs | null {
  if (!value) return null;
  const parsed = dayjs.tz(value, DATE_TIME, CAMPUS_TZ);
  return parsed.isValid() ? parsed : null;
}

export function parseServerDate(value: string | null | undefined): dayjs.Dayjs | null {
  if (!value) return null;
  const parsed = dayjs.tz(value, DATE, CAMPUS_TZ);
  return parsed.isValid() ? parsed : null;
}

/** Outbound LocalDate, e.g. "2026-08-14". */
export function toServerDate(value: dayjs.Dayjs | Date): string {
  return dayjs(value).tz(CAMPUS_TZ).format(DATE);
}

/** Outbound LocalDateTime — no offset, no trailing Z. */
export function toServerDateTime(value: dayjs.Dayjs | Date): string {
  return dayjs(value).tz(CAMPUS_TZ).format(DATE_TIME);
}

export function formatDate(value: string | null | undefined): string {
  return parseServerDate(value)?.format('D MMM YYYY') ?? '—';
}

export function formatDateTime(value: string | null | undefined): string {
  return parseServerDateTime(value)?.format('D MMM YYYY, HH:mm') ?? '—';
}

/** Mono contexts — entry logs, scan results. */
export function formatTime(value: string | null | undefined): string {
  return parseServerDateTime(value)?.format('HH:mm:ss') ?? '—';
}

/**
 * How long something has been waiting, for a queue.
 *
 * "Waiting 3 days" answers the question a reviewer actually has; the exact
 * minute a student pressed Submit does not. The precise timestamp stays
 * available on the detail view for anyone who needs it.
 *
 * Written out rather than using dayjs' relativeTime plugin, which is not
 * loaded here and would round "26 hours" to "a day" — for a queue measured in
 * days, that rounding is in the wrong direction.
 */
export function formatWaitingFor(value: string | null | undefined): string {
  const since = parseServerDateTime(value);
  if (!since) return '—';

  const now = dayjs().tz(CAMPUS_TZ);
  const minutes = now.diff(since, 'minute');
  if (minutes < 1) return 'Just now';
  if (minutes < 60) return `${minutes} min`;

  const hours = now.diff(since, 'hour');
  if (hours < 24) return `${hours} ${hours === 1 ? 'hour' : 'hours'}`;

  const days = now.diff(since, 'day');
  return `${days} ${days === 1 ? 'day' : 'days'}`;
}

/** Past this, a queue item has been ignored rather than merely queued. */
export const STALE_QUEUE_DAYS = 3;

export function isWaitingTooLong(value: string | null | undefined): boolean {
  const since = parseServerDateTime(value);
  if (!since) return false;
  return dayjs().tz(CAMPUS_TZ).diff(since, 'day') >= STALE_QUEUE_DAYS;
}

export function formatValidity(from: string, to: string | null): string {
  const start = parseServerDate(from);
  if (!start) return '—';
  // A null validTo is a standing DAILY pass, not missing data.
  if (!to) return 'Rolling daily — no end date';
  const end = parseServerDate(to);
  if (!end) return start.format('D MMM YYYY');
  return start.isSame(end, 'day')
    ? start.format('D MMM YYYY')
    : `${start.format('D MMM')} – ${end.format('D MMM YYYY')}`;
}

/** guard-service caps the entry-log range at 90 days. */
export const MAX_ENTRY_LOG_RANGE_DAYS = 90;

export function isRangeWithinEntryLogLimit(from: dayjs.Dayjs, to: dayjs.Dayjs): boolean {
  return to.diff(from, 'day') <= MAX_ENTRY_LOG_RANGE_DAYS;
}
