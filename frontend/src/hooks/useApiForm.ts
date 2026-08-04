import { useCallback } from 'react';
import type { FieldValues, Path, UseFormSetError } from 'react-hook-form';
import { ApiError, ValidationError } from '@lib/api/errors';

/**
 * Maps a typed API failure back onto the form.
 *
 * Field errors land on their inputs; object-level @AssertTrue violations, which
 * name a DTO rather than a field, become form-level messages instead of being
 * silently dropped because no input matches.
 *
 * ==========================================================================
 * IN hooks/, NOT IN features/auth/
 * ==========================================================================
 * It started in the auth feature because auth had the first forms. Nothing in
 * it is auth-specific — it maps ApiError and ValidationError, which every
 * service returns — and it is now used by campus-admin, super-admin, student
 * and by ApprovalDrawer in components/.
 *
 * Each of those was importing across a feature boundary that eslint forbids,
 * and the rule was right: a shared utility that lives inside one feature makes
 * every other feature depend on that feature's internals. The rule went
 * unenforced only because `npm run lint` could not run at all.
 *
 * Phase 4 could not import it from features/auth, which is what surfaced this.
 */
export function useApiFormErrors<T extends FieldValues>(
  setError: UseFormSetError<T>,
  setFormErrors: (messages: string[]) => void,
) {
  return useCallback(
    (error: unknown) => {
      if (error instanceof ValidationError) {
        for (const [field, message] of Object.entries(error.fieldErrors)) {
          setError(field as Path<T>, { type: 'server', message });
        }
        setFormErrors(error.formErrors);
        return;
      }
      if (error instanceof ApiError) {
        setFormErrors([error.message, ...error.formErrors]);
        return;
      }
      setFormErrors(['That request could not be completed.']);
    },
    [setError, setFormErrors],
  );
}
