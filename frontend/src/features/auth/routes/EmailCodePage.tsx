import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { Button, Field, Input } from '@ui/index';
import { FormError } from '@components/feedback';
import { authApi } from '@lib/api/services/auth.api';
import { config } from '@lib/config';
import { otpRequestSchema, type OtpRequestValues } from '../schemas/auth.schemas';
import { useApiFormErrors } from '@hooks/useApiForm';

export default function EmailCodePage() {
  const navigate = useNavigate();
  const [formErrors, setFormErrors] = useState<string[]>([]);

  const form = useForm<OtpRequestValues>({
    resolver: zodResolver(otpRequestSchema),
    // Tell the user while their attention is still on the field, not after they
    // press the button. Silent on arrival, live once they start correcting.
    mode: 'onTouched',
    reValidateMode: 'onChange',
    defaultValues: { email: '' },
  });
  const applyApiErrors = useApiFormErrors<OtpRequestValues>(form.setError, setFormErrors);

  const request = useMutation({
    mutationFn: (values: OtpRequestValues) =>
      authApi.requestOtp({ email: values.email, purpose: 'LOGIN', campusId: config.defaultCampusId }),
    // Always resolves — the backend returns the same response for an unknown
    // address and for a password-only role, deliberately, to stop enumeration.
    onSuccess: (challenge, values) =>
      navigate('/login/verify', { state: { email: values.email, challenge } }),
    onError: applyApiErrors,
  });

  return (
    <div className="flex flex-col gap-[var(--sp-4)]">
      <div>
        <h1 className="text-h1 text-[var(--ink-900)]">Sign in with a code</h1>
        <p className="text-small mt-[var(--sp-1)] text-[var(--ink-500)]">
          We will email you a 6-digit code. No password needed.
        </p>
      </div>

      <FormError messages={formErrors} />

      <form
        noValidate
        className="flex flex-col gap-[var(--sp-4)]"
        onSubmit={form.handleSubmit((values) => {
          setFormErrors([]);
          request.mutate(values);
        })}
      >
        <Field label="Email" required error={form.formState.errors.email?.message}>
          {({ id, describedBy }) => (
            <Input
              id={id}
              type="email"
              autoComplete="email"
              autoFocus
              aria-describedby={describedBy}
              invalid={!!form.formState.errors.email}
              {...form.register('email')}
            />
          )}
        </Field>

        <Button type="submit" block loading={request.isPending}>
          Send code
        </Button>
      </form>

      <div className="flex flex-col gap-[var(--sp-2)] border-t border-[var(--border)] pt-[var(--sp-4)] text-small">
        <Link to="/login" className="text-[var(--brand-600)] hover:underline">
          Have a password? Sign in with it
        </Link>
        <Link to="/register/visitor" className="text-[var(--brand-600)] hover:underline">
          Visiting for the first time? Register
        </Link>
      </div>
    </div>
  );
}
