import { gatepass } from './gatepass';
import { guard } from './guard';

/**
 * Attendance for one event, assembled from TWO services.
 *
 * Neither service can answer this alone:
 *
 *   gatepass-service  knows how many passes were issued, and the event's dates
 *   guard-service     knows who actually scanned in, and on which days
 *
 * "Never showed" is registered − turned-up, so it needs both. guard-service
 * takes `registeredCount` as a query parameter for exactly this reason, and it
 * DEFAULTS TO ZERO — call it without and you get a clean, plausible, entirely
 * wrong answer in which nobody ever failed to attend. That is a worse failure
 * than an error, because nothing about it looks broken.
 *
 * Doing the join here means both screens that need it cannot do it differently.
 */
export async function eventAttendanceFor(eventId) {
  const summary = await gatepass.attendanceSummary(eventId);

  const attendance = await guard.eventAttendance(eventId, {
    from: summary.validFrom,
    to: summary.validTo,
    eventName: summary.eventName,
    registeredCount: summary.registeredCount,
  });

  return { ...attendance, summary };
}
