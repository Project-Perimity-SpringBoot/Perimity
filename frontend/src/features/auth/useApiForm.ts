import { useCallback } from 'react';
import type { FieldValues, Path, UseFormSetError } from 'react-hook-form';
import { ApiError, ValidationError } from '@lib/api/errors';

/**
 * Maps a typed API failure back onto the form.
 *
 * Field errors land on their inputs; object-level @AssertTrue violations, which
 * name a DTO rather than a field, become form-level messages instead of being
 * silently dropped because no input matches.
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
