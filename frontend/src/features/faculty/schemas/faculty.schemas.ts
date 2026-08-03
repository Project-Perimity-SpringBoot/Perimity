import { z } from 'zod';
import { LIMITS } from '@lib/validation/patterns';

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
