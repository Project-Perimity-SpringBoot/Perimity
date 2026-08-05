import { z } from 'zod';
import dayjs from 'dayjs';
import { LIMITS, RX } from '@lib/validation/patterns';
import { parseServerDate } from '@lib/format/datetime';
import { GENDERS } from '@/types/enums';

/**
 * StudentProfileUpdateDto, which is StudentProfileCreateRequest minus userId
 * and campusId — both immutable once the profile exists.
 *
 * ==========================================================================
 * FOUR OF THESE FIELDS PAUSE THE STUDENT'S PASS
 * ==========================================================================
 * name, photo, government ID and department. Changing any of them moves every
 * active pass to PAUSED until staff re-verify, because all four are things a
 * guard compares against the person standing in front of them. A pass that
 * still says "A. Sharma, Computer Science" after both were edited is a pass
 * that can be lent to somebody else.
 *
 * PAUSE_TRIGGERING_FIELDS is exported so the edit screen can mark exactly those
 * inputs without a second list drifting out of step with this one.
 *
 * NOTE: name is NOT on this DTO — it lives on the auth-service user record, not
 * the profile. The edit screen says so rather than rendering a field that
 * cannot be saved here.
 */
export const PAUSE_TRIGGERING_FIELDS = ['departmentId', 'govId', 'photo', 'name'] as const;
export type PauseTriggeringField = (typeof PAUSE_TRIGGERING_FIELDS)[number];

export const studentProfileSchema = z.object({
  departmentId: z
    .union([z.coerce.number().int().positive(), z.literal('')])
    .optional(),

  rollNo: z
    .string()
    .max(LIMITS.identifierCode.max, 'Roll numbers are at most 32 characters')
    .regex(RX.IDENTIFIER_CODE, 'Use letters, numbers and hyphens only')
    .or(z.literal(''))
    .optional(),

  /**
   * Write-only. The server returns govIdMasked and never the full value, so
   * this field is always blank on load — an empty string means "leave it as it
   * is", not "clear it".
   */
  govId: z
    .string()
    .min(4, 'A government ID is at least 4 characters')
    .max(64, 'That is longer than any government ID we accept')
    .or(z.literal(''))
    .optional(),

  /*
   * NO address here. It moved to studentSelfDetailsSchema below, because it is
   * part of the record faculty verify — editing it has to reset that
   * verification, and this schema's screen sends a partial update that cannot.
   */
});

export type StudentProfileValues = z.infer<typeof studentProfileSchema>;

/* ======================================================================
 * SELF-DECLARED DETAILS — StudentSelfDetailsDto
 *
 * A transcription of the server DTO, cross-field rules included. Every
 * rule below exists on the server too; this copy only moves the message
 * from a 400 response to the field the user is looking at.
 *
 * WHOLE-OBJECT, unlike studentProfileSchema above which patches. Every
 * field is sent on every save.
 * ====================================================================== */

/**
 * Returns the ZodString only — never a union with ZodOptional.
 *
 * A single helper taking a `required` flag returns
 * `ZodString | ZodOptional<ZodString>`, and z.infer then widens EVERY name to
 * `string | undefined`, including the required ones. The optional case is
 * applied at the call site instead so each field keeps its real type.
 */
const namePart = (label: string) =>
  z
    .string()
    .trim()
    .max(LIMITS.personNamePart.max, `Keep the ${label} under 60 characters`)
    .regex(RX.PERSON_NAME_PART, 'Use letters, spaces, apostrophes, hyphens and full stops only');

/**
 * Nobody at a campus was born before 1900 or within the last ten years.
 * Deliberately loose — this catches a mistyped century, not an unusual age.
 * Mirrors isDateOfBirthPlausible() on the server.
 *
 * parseServerDate, not new Date(). `new Date('2004-08-19')` parses as UTC
 * midnight, so in any negative-offset zone the date reads as the day before —
 * which would reject a birthday that is exactly on a boundary and, worse, do it
 * for some users and not others. parseServerDate reads it in the campus zone,
 * which is the zone the value was written in.
 */
const MIN_DOB = '1900-01-01';
const isPlausibleDob = (value: string): boolean => {
  const parsed = parseServerDate(value);
  if (!parsed) return false;
  const floor = parseServerDate(MIN_DOB);
  return Boolean(floor) && parsed.isAfter(floor) && parsed.isBefore(dayjs().subtract(10, 'year'));
};

export const studentSelfDetailsSchema = z
  .object({
    firstName: namePart('first name').min(1, 'First name is required'),
    middleName: namePart('middle name').optional(),
    lastName: namePart('last name').min(1, 'Last name is required'),

    dateOfBirth: z
      .string()
      .min(1, 'Date of birth is required')
      .refine((v) => parseServerDate(v) !== null, 'That is not a valid date')
      .refine(isPlausibleDob, 'Check the year — that looks wrong'),

    gender: z.enum(GENDERS, { message: 'Choose an option' }),

    address: z
      .string()
      .trim()
      .min(1, 'Address is required')
      .max(LIMITS.address.max, 'Keep the address under 250 characters'),

    phoneCountryCode: z
      .string()
      .trim()
      .min(1, 'Country code is required')
      .regex(RX.PHONE_COUNTRY_CODE, 'Country code must look like +91'),

    phoneNumber: z
      .string()
      .trim()
      .min(1, 'Phone number is required')
      .regex(RX.PHONE_NATIONAL, 'Digits only, without the country code'),

    altPhoneCountryCode: z
      .string()
      .trim()
      .regex(RX.PHONE_COUNTRY_CODE, 'Country code must look like +91')
      .or(z.literal(''))
      .optional(),

    altPhoneNumber: z
      .string()
      .trim()
      .regex(RX.PHONE_NATIONAL, 'Digits only, without the country code')
      .or(z.literal(''))
      .optional(),
  })
  /*
   * The 10-digit rule belongs to +91, not to everyone. Enforcing it on the
   * field itself would make this form unusable for any other country, which a
   * product calling itself campus-agnostic cannot do.
   */
  .refine(
    (v) => v.phoneCountryCode !== '+91' || RX.PHONE_NATIONAL_IN.test(v.phoneNumber),
    {
      path: ['phoneNumber'],
      message: 'An Indian mobile number is 10 digits and starts with 6, 7, 8 or 9',
    },
  )
  .refine(
    (v) =>
      !v.altPhoneNumber
      || v.altPhoneCountryCode !== '+91'
      || RX.PHONE_NATIONAL_IN.test(v.altPhoneNumber),
    {
      path: ['altPhoneNumber'],
      message: 'An Indian mobile number is 10 digits and starts with 6, 7, 8 or 9',
    },
  )
  /*
   * A second number is a code AND a number, or neither. A number with no code
   * cannot be dialled and a code with no number is not a contact.
   */
  .refine(
    (v) => Boolean(v.altPhoneCountryCode?.trim()) === Boolean(v.altPhoneNumber?.trim()),
    {
      path: ['altPhoneNumber'],
      message: 'Give both a country code and a number, or leave both empty',
    },
  );

export type StudentSelfDetailsValues = z.infer<typeof studentSelfDetailsSchema>;
