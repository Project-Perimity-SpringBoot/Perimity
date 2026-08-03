import { z } from 'zod';
import { LIMITS, RX } from '@lib/validation/patterns';

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

  address: z
    .string()
    .max(LIMITS.address.max, 'Keep the address under 250 characters')
    .or(z.literal(''))
    .optional(),
});

export type StudentProfileValues = z.infer<typeof studentProfileSchema>;
