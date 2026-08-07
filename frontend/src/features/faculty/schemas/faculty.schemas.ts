import { z } from 'zod';
import { LIMITS, RX } from '@lib/validation/patterns';

/**
 * EventCreateRequest / EventUpdateRequest.
 *
 * `createdBy` and `campusId` are absent on purpose — both are @JsonIgnore
 * server-side and taken from the token. A body that named its own creator would
 * let one faculty member file an event under another's name.
 *
 * ==========================================================================
 * THE DATE RANGE BELONGS TO THE EVENT, NOT TO A ROW
 * ==========================================================================
 * When an event visitor batch is uploaded against this event, every attendee in
 * the sheet gets THIS validFrom/validTo. The sheet has no date columns and must
 * not grow any: 580 rows each carrying their own dates is 580 chances for one
 * attendee to hold a pass that outlives the programme.
 */
export const eventSchema = z
  .object({
    name: z
      .string()
      .trim()
      .min(LIMITS.eventName.min, 'Give the event a name of at least 3 characters')
      .max(LIMITS.eventName.max, `Keep the name under ${LIMITS.eventName.max} characters`),

    description: z
      .string()
      .trim()
      .max(LIMITS.eventDescription.max, `Keep the description under ${LIMITS.eventDescription.max} characters`)
      .optional()
      .or(z.literal('')),

    validFrom: z.string().min(1, 'Pick a start date'),
    validTo: z.string().min(1, 'Pick an end date'),
  })
  /**
   * Mirrors the server's @ValidDateRange. Checked here as well so the faculty
   * member is told before a round trip, not after — and reported against
   * validTo rather than the form root, so the error lands on the field the user
   * would change to fix it.
   */
  .refine((values) => values.validTo >= values.validFrom, {
    message: 'The end date cannot be before the start date',
    path: ['validTo'],
  });

export type EventValues = z.infer<typeof eventSchema>;

/**
 * Step 1 of bulk onboarding. Not sent as JSON — bulkApi.validate builds the
 * multipart form — but validated here so the two conditional rules are stated
 * once and enforced before a 5 MB upload leaves the browser.
 *
 * eventId is required exactly when passType is EVENT, and forbidden otherwise.
 * The server rejects both mistakes; catching them here is the difference
 * between an inline message and a failed upload.
 */
export const bulkUploadSchema = z
  .object({
    passType: z.enum(['DAILY', 'EVENT']),
    eventId: z.coerce.number().int().positive().optional(),
  })
  .refine((values) => values.passType !== 'EVENT' || values.eventId !== undefined, {
    message: 'Choose which event these visitors are attending',
    path: ['eventId'],
  });

export type BulkUploadValues = z.infer<typeof bulkUploadSchema>;

/**
 * One student, entered by hand.
 *
 * ==========================================================================
 * THIS FORM DRIVES TWO CREATES, NOT ONE
 * ==========================================================================
 * A student is two records: the login account in auth-service, and the identity
 * profile in user-service. This schema covers both halves of the form, and the
 * screen submits them in order - account first, because the profile needs the
 * userId the account create returns.
 *
 * campusId appears in neither half. It comes from the faculty member's token.
 *
 * NO SEMESTER FIELD. StudentProfileCreateDto says it plainly: the SRS excludes
 * it, it is not needed for access control, and it must never appear in any form.
 */
export const addStudentSchema = z.object({
  /* ---- the login account (auth-service) ---- */
  name: z
    .string()
    .min(LIMITS.personName.min, 'Name is required')
    .max(LIMITS.personName.max, 'Name may be at most 120 characters')
    .regex(RX.PERSON_NAME, 'Use letters, spaces, hyphens and apostrophes only'),

  email: z
    .string()
    .min(1, 'Email is required')
    .max(LIMITS.email.max, 'That address is too long')
    .regex(RX.EMAIL, 'Enter a valid email address'),

  /*
   * RX.PHONE, not the Indian national rule.
   *
   * This field lands on auth-service's UserCreateDto.phone, which accepts
   * ^\+?[1-9]\d{6,14}$ - a country code and 7 to 15 digits. Demanding a bare
   * ten-digit Indian number here refused +919876543210, the format the API
   * documents and the one people type, while the server would have taken it.
   *
   * The visitor form is deliberately different: gatepass-service enforces the
   * Indian rule on visitorPhone, so its form matches that. This one has no
   * country-code field beside it, so the code has to live in the number.
   */
  phone: z
    .string()
    .regex(RX.PHONE, 'Include the country code, e.g. +919876543210')
    .or(z.literal(''))
    .optional(),

  temporaryPassword: z
    .string()
    .regex(
      RX.PASSWORD_POLICY,
      'At least 8 characters with an uppercase letter, a lowercase letter and a number',
    ),

  /* ---- the identity profile (user-service) ---- */
  departmentId: z.union([z.coerce.number().int().positive(), z.literal('')]).optional(),

  rollNo: z
    .string()
    .max(LIMITS.identifierCode.max, 'Roll numbers are at most 32 characters')
    .regex(RX.IDENTIFIER_CODE, 'Use letters, numbers and hyphens only')
    .or(z.literal(''))
    .optional(),

  /*
   * Exactly 12 digits or empty - StudentProfileCreateDto enforces ^$|^\d{12}$.
   * Worth knowing that is an Aadhaar-shaped assumption in a product that calls
   * itself campus-agnostic; it will reject any other country's ID.
   */
  govId: z
    .string()
    .regex(RX.GOV_ID, 'A government ID must be exactly 12 digits')
    .optional(),

  address: z
    .string()
    .max(LIMITS.address.max, 'Keep the address under 250 characters')
    .or(z.literal(''))
    .optional(),
});
export type AddStudentValues = z.infer<typeof addStudentSchema>;
