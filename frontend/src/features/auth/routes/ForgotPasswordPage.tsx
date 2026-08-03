import { useState } from 'react';
import { Link } from 'react-router';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { MailCheck } from 'lucide-react';
import { Button, Field, Input } from '@ui/index';
import { EmptyState, FormError } from '@components/feedback';
import { authApi } from '@lib/api/services/auth.api';
import { forgotPasswordSchema, type ForgotPasswordValues } from '../schemas/auth.schemas';
import { useApiFormErrors } from '@hooks/useApiForm';

export default function ForgotPasswordPage() {
  const [formErrors, setFormErrors] = useState<string[]>([]);
  const form = useForm<ForgotPasswordValues>({
    resolver: zodResolver(forgotPasswordSchema),
    // Tell the user while their attention is still on the field, not after they
    // press the button. Silent on arrival, live once they start correcting.
    mode: 'onTouched',
    reValidateMode: 'onChange',
    defaultValues: { email: '' },
  });
  const applyApiErrors = useApiFormErrors<ForgotPasswordValues>(form.setError, setFormErrors);

  const request = useMutation({
    mutationFn: (values: ForgotPasswordValues) => authApi.requestPasswordReset(values),
    onError: applyApiErrors,
  });

  if (request.isSuccess) {
    return (
      <EmptyState
        icon={MailCheck}
        heading="Check your email"
        description="If that address has an account, a reset link is on its way. The link is valid for 30 minutes."
        action={
          <Button asChild variant="secondary">
            <Link to="/login">Back to sign in</Link>
          </Button>
        }
      />
    );
  }

  return (
    <div className="flex flex-col gap-[var(--sp-4)]">
      <div>
        <h1 className="text-h1 text-[var(--ink-900)]">Reset your password</h1>
        <p className="text-small mt-[var(--sp-1)] text-[var(--ink-500)]">
          Enter your email and we will send you a link.
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
          Send reset link
        </Button>
      </form>

      <Link to="/login" className="text-small text-[var(--brand-600)] hover:underline">
        Back to sign in
      </Link>
    </div>
  );
}
