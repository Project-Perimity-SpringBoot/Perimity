import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { Button, Field, Input } from '@ui/index';
import { FormError } from '@components/feedback';
import { authApi } from '@lib/api/services/auth.api';
import { config } from '@lib/config';
import { visitorRegistrationSchema, type VisitorRegistrationValues } from '../schemas/auth.schemas';
import { useApiFormErrors } from '@hooks/useApiForm';

export default function VisitorRegisterPage() {
  const navigate = useNavigate();
  const [formErrors, setFormErrors] = useState<string[]>([]);

  const form = useForm<VisitorRegistrationValues>({
    resolver: zodResolver(visitorRegistrationSchema),
    // Tell the user while their attention is still on the field, not after they
    // press the button. Silent on arrival, live once they start correcting.
    mode: 'onTouched',
    reValidateMode: 'onChange',
    defaultValues: { email: '', name: '' },
  });
  const applyApiErrors = useApiFormErrors<VisitorRegistrationValues>(form.setError, setFormErrors);

  const register = useMutation({
    mutationFn: async (values: VisitorRegistrationValues) => {
      // A visitor never sets a password; the account exists only so an OTP can
      // be issued against it. campusId comes from configuration because
      // GET /campuses requires a token a first-time visitor does not have.
      await authApi.registerVisitor({
        email: values.email,
        name: values.name,
        campusId: config.defaultCampusId,
      });
      /*
       * The account now exists. If the code request fails after that, the
       * visitor must NOT be dropped back onto a registration form telling them
       * to check their connection - registering again would only fail on the
       * duplicate, and their account is already there.
       *
       * Observed: this second call comes back net::ERR_ABORTED after the
       * register succeeds, and the whole mutation then reports a network
       * failure. Root cause not yet established. Whatever aborts it, the
       * recoverable state is the verify screen, which already has a working
       * "resend code" button.
       */
      try {
        return await authApi.requestOtp({
          email: values.email,
          purpose: 'LOGIN',
          campusId: config.defaultCampusId,
        });
      } catch {
        return null;
      }
    },
    onSuccess: (challenge, values) =>
      navigate('/login/verify', { state: { email: values.email, challenge } }),
    onError: applyApiErrors,
  });

  return (
    <div className="flex flex-col gap-[var(--sp-4)]">
      <div>
        <h1 className="text-h1 text-[var(--ink-900)]">Register as a visitor</h1>
        <p className="text-small mt-[var(--sp-1)] text-[var(--ink-500)]">
          You will sign in with an email code. Visitors never set a password.
        </p>
      </div>

      <FormError messages={formErrors} />

      <form
        noValidate
        className="flex flex-col gap-[var(--sp-4)]"
        onSubmit={form.handleSubmit((values) => {
          setFormErrors([]);
          register.mutate(values);
        })}
      >
        <Field label="Full name" required error={form.formState.errors.name?.message}>
          {({ id, describedBy }) => (
            <Input
              id={id}
              autoComplete="name"
              autoFocus
              aria-describedby={describedBy}
              invalid={!!form.formState.errors.name}
              {...form.register('name')}
            />
          )}
        </Field>

        <Field label="Email" required error={form.formState.errors.email?.message}>
          {({ id, describedBy }) => (
            <Input
              id={id}
              type="email"
              autoComplete="email"
              aria-describedby={describedBy}
              invalid={!!form.formState.errors.email}
              {...form.register('email')}
            />
          )}
        </Field>

        <Button type="submit" block loading={register.isPending}>
          Continue
        </Button>
      </form>

      <Link to="/login/code" className="text-small text-[var(--brand-600)] hover:underline">
        Already registered? Sign in with a code
      </Link>
    </div>
  );
}
