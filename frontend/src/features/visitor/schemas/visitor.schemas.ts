import { z } from 'zod';
import { LIMITS, RX } from '@lib/validation/patterns';

/**
 * VisitorRequestCreateRequest.
 *
 * `campusId` is absent on purpose — it is @JsonIgnore server-side and taken
 * from the token.
 *
 * `visitorEmail` IS sent: VisitorRequestCreateDto marks it @NotBlank, so the
 * server requires it in the body rather than reading the token. The form
 * prefills it from the signed-in identity and renders it read-only, so the
 * value that goes up is the address that was actually verified.
 *
 * Worth flagging to gatepass: because the server trusts the body, a signed-in
 * visitor could file a request under a different address by calling the API
 * directly. The read-only field closes it in the UI, not in the product.
 *
 * ==========================================================================
 * NO SEMESTER. NO DOCUMENT UPLOAD. NO PASSWORD.
 * ==========================================================================
 * Not oversights. A visitor has no semester and never sets a password — email
 * OTP is the entire authentication story for this role. Documents are not
 * collected at request time; the host approves on the strength of the purpose
 * and the dates, and asking a stranger to upload ID before anyone has agreed to
 * meet them is friction that buys nothing.
 */
export const visitorRequestSchema = z
  .object({
    visitorName: z
      .string()
      .trim()
      .min(LIMITS.personName.min, 'Give your full name')
      .max(LIMITS.personName.max, `Keep it under ${LIMITS.personName.max} characters`)
      .regex(RX.PERSON_NAME, 'Use letters, spaces, apostrophes and hyphens only'),

    /** Prefilled from the token and read-only. See the note above. */
    visitorEmail: z
      .string()
      .trim()
      .min(1, 'Sign in again — your email is missing')
      .max(LIMITS.email.max)
      .regex(RX.EMAIL, 'That does not look like an email address'),

    /**
     * Optional, and the server agrees. The host can already reach the visitor
     * by email — a phone number is a convenience for the day of the visit, not
     * a requirement for making the request.
     */
    visitorPhone: z
      .string()
      .trim()
      .regex(RX.PHONE, 'Enter a phone number with country code, e.g. +919876543210')
      .or(z.literal(''))
      .optional(),

    purpose: z
      .string()
      .trim()
      .min(LIMITS.purpose.min, 'Say briefly why you are visiting — your host reads this')
      .max(LIMITS.purpose.max, `Keep it under ${LIMITS.purpose.max} characters`),

    /**
     * The campus being visited. Chosen, never typed.
     *
     * Replaces the host picker. A visitor rarely knows which faculty member to
     * name, and naming the wrong one parked the request in an inbox nobody
     * watched. Any faculty of this campus can now verify it, and whoever does
     * is recorded as the approver server-side.
     *
     * Required here even though VisitorRequestCreateDto marks campusId
     * @JsonIgnore: the value is sent as a query parameter rather than in the
     * body, and a request with no campus has no queue to land in.
     */
    campusId: z.coerce
      .number({ invalid_type_error: 'Choose the campus you are visiting' })
      .int()
      .positive('Choose the campus you are visiting'),

    visitFrom: z.string().min(1, 'Pick the first day of your visit'),
    visitTo: z.string().min(1, 'Pick the last day of your visit'),
  })
  /**
   * Mirrors the server's @ValidDateRange. Reported against visitTo rather than
   * the form root so the message lands on the field the visitor would change.
   */
  .refine((values) => values.visitTo >= values.visitFrom, {
    message: 'The last day cannot be before the first day',
    path: ['visitTo'],
  });

export type VisitorRequestValues = z.infer<typeof visitorRequestSchema>;
