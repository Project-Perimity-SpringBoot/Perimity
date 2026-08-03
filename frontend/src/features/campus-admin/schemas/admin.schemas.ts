import { z } from 'zod';
import { LIMITS, RX } from '@lib/validation/patterns';
import { ROLES } from '@/types/enums';

const email = z
  .string()
  .min(1, 'Email is required')
  .max(LIMITS.email.max, 'That address is too long')
  .regex(RX.EMAIL, 'Enter a valid email address');

/**
 * UserCreateDto. `temporaryPassword` is required for every role except VISITOR
 * and forbidden for VISITOR — a cross-field @AssertTrue server-side, mirrored
 * here so the user is told before the round trip rather than by a 400 naming a
 * DTO.
 */
export const userCreateSchema = z
  .object({
    email,
    name: z
      .string()
      .min(LIMITS.personName.min, 'Name is required')
      .max(LIMITS.personName.max, 'Name may be at most 120 characters')
      .regex(RX.PERSON_NAME, 'Use letters, spaces, hyphens and apostrophes only'),
    phone: z.string().regex(RX.PHONE, 'Enter a valid phone number').or(z.literal('')).optional(),
    role: z.enum(ROLES),
    temporaryPassword: z.string().or(z.literal('')).optional(),
  })
  .superRefine((values, ctx) => {
    if (values.role === 'VISITOR') {
      if (values.temporaryPassword) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['temporaryPassword'],
          message: 'A visitor signs in with an emailed code and has no password',
        });
      }
      return;
    }
    if (!values.temporaryPassword) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['temporaryPassword'],
        message: 'A temporary password is required for this role',
      });
      return;
    }
    if (!RX.PASSWORD_POLICY.test(values.temporaryPassword)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['temporaryPassword'],
        message: 'At least 8 characters with an uppercase letter, a lowercase letter and a number',
      });
    }
  });
export type UserCreateValues = z.infer<typeof userCreateSchema>;

/** UserUpdateDto. email, role and campusId are immutable and are not sent. */
export const userUpdateSchema = z.object({
  name: z
    .string()
    .min(LIMITS.personName.min, 'Name is required')
    .max(LIMITS.personName.max, 'Name may be at most 120 characters')
    .regex(RX.PERSON_NAME, 'Use letters, spaces, hyphens and apostrophes only'),
  phone: z.string().regex(RX.PHONE, 'Enter a valid phone number').or(z.literal('')).optional(),
});
export type UserUpdateValues = z.infer<typeof userUpdateSchema>;

export const departmentSchema = z.object({
  code: z
    .string()
    .min(1, 'A code is required')
    .max(LIMITS.departmentCode.max, 'Codes are at most 32 characters')
    .regex(RX.DEPARTMENT_CODE, 'Letters, numbers, spaces, dots, underscores and hyphens'),
  name: z
    .string()
    .min(1, 'A name is required')
    .max(LIMITS.departmentName.max, 'Names are at most 150 characters')
    .regex(RX.TITLE_USER, 'Use letters, numbers and basic punctuation'),
  active: z.boolean(),
});
export type DepartmentValues = z.infer<typeof departmentSchema>;

export const gateSchema = z.object({
  name: z
    .string()
    .min(1, 'A gate name is required')
    .max(LIMITS.gateName.max, 'Gate names are at most 100 characters')
    .regex(RX.DISPLAY_NAME, 'Use letters, numbers and basic punctuation'),
  location: z
    .string()
    .max(LIMITS.gateLocation.max, 'Keep the location under 150 characters')
    .or(z.literal(''))
    .optional(),
  active: z.boolean(),
});
export type GateValues = z.infer<typeof gateSchema>;

/**
 * BlocklistCreateDto. At least one of email or phone must be present
 * (@AssertTrue), and the reason is mandatory at five characters — an entry
 * nobody can justify six months later is an entry that should not exist.
 */
export const blocklistSchema = z
  .object({
    email: email.or(z.literal('')).optional(),
    phone: z.string().regex(RX.PHONE, 'Enter a valid phone number').or(z.literal('')).optional(),
    reason: z
      .string()
      .min(LIMITS.blocklistReason.min, 'Give a reason of at least 5 characters')
      .max(LIMITS.blocklistReason.max, 'Keep the reason under 500 characters'),
  })
  .refine((values) => Boolean(values.email) || Boolean(values.phone), {
    path: ['email'],
    message: 'Give an email address or a phone number',
  });
export type BlocklistValues = z.infer<typeof blocklistSchema>;

/** UserStatusUpdateDto / CampusStatusUpdateDto both require a reason. */
export const statusChangeSchema = z.object({
  reason: z
    .string()
    .min(LIMITS.reason.min, 'Give a reason of at least 3 characters')
    .max(LIMITS.reason.max, 'Keep the reason under 500 characters'),
});
export type StatusChangeValues = z.infer<typeof statusChangeSchema>;
