import { z } from 'zod';
import { LIMITS, RX } from '@lib/validation/patterns';

/**
 * Every rule is a copy of the server's. A stricter client rule rejects input
 * the backend would accept; a looser one produces a 400 the user cannot act on.
 */

const email = z
  .string()
  .min(1, 'Email is required')
  .max(LIMITS.email.max, 'That address is too long')
  .regex(RX.EMAIL, 'Enter a valid email address');

const personName = z
  .string()
  .min(LIMITS.personName.min, 'Name is required')
  .max(LIMITS.personName.max, 'Name may be at most 120 characters')
  .regex(RX.PERSON_NAME, 'Use letters, spaces, hyphens and apostrophes only');

const phone = z
  .string()
  .regex(RX.PHONE, 'Enter a valid phone number')
  .optional()
  .or(z.literal(''));

/**
 * 8+ with upper, lower and a digit. NOT 12, and NO symbol — the design mockup's
 * checklist contradicts the backend regex, and the backend wins.
 */
const newPassword = z
  .string()
  .min(LIMITS.password.min, 'Use at least 8 characters')
  .max(LIMITS.password.max, 'Passwords are capped at 72 characters')
  .regex(RX.PASSWORD_POLICY, 'Include an uppercase letter, a lowercase letter and a number');

export const PASSWORD_RULES = [
  { label: 'At least 8 characters', test: (v: string) => v.length >= 8 },
  { label: 'One uppercase letter', test: (v: string) => /[A-Z]/.test(v) },
  { label: 'One lowercase letter', test: (v: string) => /[a-z]/.test(v) },
  { label: 'One number', test: (v: string) => /\d/.test(v) },
  { label: 'At most 72 characters', test: (v: string) => v.length > 0 && v.length <= 72 },
] as const;

export const loginSchema = z.object({
  email,
  // The policy is deliberately not applied on login: it would advertise the
  // password shape and lock out any legacy account that predates the rule.
  password: z.string().min(1, 'Password is required').max(LIMITS.password.max),
});
export type LoginValues = z.infer<typeof loginSchema>;

export const otpRequestSchema = z.object({ email });
export type OtpRequestValues = z.infer<typeof otpRequestSchema>;

export const otpVerifySchema = z.object({
  code: z.string().regex(RX.OTP_CODE, 'The code is exactly 6 digits'),
});
export type OtpVerifyValues = z.infer<typeof otpVerifySchema>;

export const visitorRegistrationSchema = z.object({
  email,
  name: personName,
  phone,
});
export type VisitorRegistrationValues = z.infer<typeof visitorRegistrationSchema>;

export const forgotPasswordSchema = z.object({ email });
export type ForgotPasswordValues = z.infer<typeof forgotPasswordSchema>;

export const resetPasswordSchema = z
  .object({
    token: z.string().regex(RX.SHA256_HEX, 'This reset link is malformed or incomplete'),
    newPassword,
    confirmPassword: z.string(),
  })
  .refine((v) => v.newPassword === v.confirmPassword, {
    path: ['confirmPassword'],
    message: 'The two passwords do not match',
  });
export type ResetPasswordValues = z.infer<typeof resetPasswordSchema>;

export const changePasswordSchema = z
  .object({
    currentPassword: z.string().min(1, 'Your current password is required'),
    newPassword,
    confirmPassword: z.string(),
  })
  .refine((v) => v.newPassword === v.confirmPassword, {
    path: ['confirmPassword'],
    message: 'The two passwords do not match',
  })
  .refine((v) => v.newPassword !== v.currentPassword, {
    path: ['newPassword'],
    message: 'The new password must be different from the current one',
  });
export type ChangePasswordValues = z.infer<typeof changePasswordSchema>;
