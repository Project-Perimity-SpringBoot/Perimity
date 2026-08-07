import { z } from 'zod';
import { LIMITS, RX } from '@lib/validation/patterns';

/**
 * CampusCreateDto. `code` is IMMUTABLE after creation — it is embedded in every
 * storage path for the campus, so renaming it would orphan every file that
 * campus ever uploaded. The update DTO rejects it outright.
 */
export const campusSchema = z.object({
  code: z
    .string()
    .length(4, 'Campus code must be exactly 4 characters')
    .regex(/^[A-Z]{4}$/, 'Campus code must contain capital letters only (A-Z)'),
  name: z
    .string()
    .min(1, 'A name is required')
    .max(LIMITS.campusName.max, 'Names are at most 150 characters')
    .regex(RX.DISPLAY_NAME, 'Use letters, numbers and basic punctuation'),
  address: z.string().max(LIMITS.address.max, 'Keep the address under 250 characters').or(z.literal('')).optional(),
  contactEmail: z.string().regex(RX.EMAIL, 'Enter a valid email address').or(z.literal('')).optional(),
  /*
   * RX.PHONE, not a hand-rolled ten-digit rule.
   *
   * Every backend field this reaches uses ^\+?[1-9]\d{6,14}$ - an optional
   * country code and 7 to 15 digits. The old /^\d{10}$/ here was wrong in
   * both directions: it REFUSED +919876543210, which is the format the API
   * documents and the one people actually type, and it ACCEPTED 0123456789,
   * which the server then rejected with a 400 the form could not explain.
   *
   * Any country code is allowed on purpose. Nothing about this product is
   * India-only, and a visitor from anywhere may be given a pass.
   */
  contactPhone: z
    .string()
    .regex(RX.PHONE, 'Include the country code, e.g. +919876543210')
    .or(z.literal(''))
    .optional(),
});
export type CampusValues = z.infer<typeof campusSchema>;

/** UserCreateDto, narrowed to the first Campus Admin of a new campus. */
export const firstAdminSchema = z.object({
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
  temporaryPassword: z
    .string()
    .min(LIMITS.password.min, 'Use at least 8 characters')
    .max(LIMITS.password.max, 'Passwords are capped at 72 characters')
    .regex(RX.PASSWORD_POLICY, 'Include an uppercase letter, a lowercase letter and a number'),
});
export type FirstAdminValues = z.infer<typeof firstAdminSchema>;

export const createAdminSchema = z.object({
  campusId: z.coerce.number().min(1, 'Please select a campus'),
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
  temporaryPassword: z
    .string()
    .min(LIMITS.password.min, 'Use at least 8 characters')
    .max(LIMITS.password.max, 'Passwords are capped at 72 characters')
    .regex(RX.PASSWORD_POLICY, 'Include an uppercase letter, a lowercase letter and a number'),
});
export type CreateAdminValues = z.infer<typeof createAdminSchema>;

export const adminEditSchema = z.object({
  name: z
    .string()
    .min(LIMITS.personName.min, 'Name is required')
    .max(LIMITS.personName.max, 'Name may be at most 120 characters')
    .regex(RX.PERSON_NAME, 'Use letters, spaces, hyphens and apostrophes only'),
  phone: z.string().regex(RX.PHONE, 'Include the country code, e.g. +919876543210').or(z.literal('')).optional(),
});
export type AdminEditValues = z.infer<typeof adminEditSchema>;

/**
 * CampusStatusUpdateDto requires a reason of 3–500 characters. Kept here rather
 * than inlined so the confirm button and the server agree on the threshold.
 */
export const statusChangeFallback = { minReason: LIMITS.reason.min } as const;
