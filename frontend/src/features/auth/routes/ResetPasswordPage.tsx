import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { CheckCircle2 } from 'lucide-react';
import { Button, Field, Input } from '@ui/index';
import { EmptyState, FormError } from '@components/feedback';
import { authApi } from '@lib/api/services/auth.api';
import { resetPasswordSchema, type ResetPasswordValues } from '../schemas/auth.schemas';
import { useApiFormErrors } from '@hooks/useApiForm';
import { PasswordRulesList } from './PasswordRulesList';

export default function ResetPasswordPage() {
  const [params] = useSearchParams();
  const [formErrors, setFormErrors] = useState<string[]>([]);

  const form = useForm<ResetPasswordValues>({
    resolver: zodResolver(resetPasswordSchema),
    // Tell the user while their attention is still on the field, not after they
    // press the button. Silent on arrival, live once they start correcting.
    mode: 'onTouched',
    reValidateMode: 'onChange',
    defaultValues: { token: params.get('token') ?? '', newPassword: '', confirmPassword: '' },
  });
  const applyApiErrors = useApiFormErrors<ResetPasswordValues>(form.setError, setFormErrors);
  const newPassword = form.watch('newPassword');

  const reset = useMutation({
    mutationFn: (values: ResetPasswordValues) => authApi.confirmPasswordReset(values),
    onError: applyApiErrors,
  });

  if (reset.isSuccess) {
    return (
      <EmptyState
        icon={CheckCircle2}
        heading="Password updated"
        description="Sign in with your new password."
        action={
          <Button asChild>
            <Link to="/login">Sign in</Link>
          </Button>
        }
      />
    );
  }

  return (
    <div className="flex flex-col gap-[var(--sp-4)]">
      <h1 className="text-h1 text-[var(--ink-900)]">Choose a new password</h1>

      <FormError messages={formErrors} />

      <form
        noValidate
        className="flex flex-col gap-[var(--sp-4)]"
        onSubmit={form.handleSubmit((values) => {
          setFormErrors([]);
          reset.mutate(values);
        })}
      >
        <input type="hidden" {...form.register('token')} />
        {form.formState.errors.token && (
          <FormError messages={[form.formState.errors.token.message ?? '']} />
        )}

        <Field label="New password" required error={form.formState.errors.newPassword?.message}>
          {({ id, describedBy }) => (
            <Input
              id={id}
              type="password"
              autoComplete="new-password"
              autoFocus
              aria-describedby={describedBy}
              invalid={!!form.formState.errors.newPassword}
              {...form.register('newPassword')}
            />
          )}
        </Field>

        <PasswordRulesList value={newPassword} />

        <Field label="Confirm new password" required error={form.formState.errors.confirmPassword?.message}>
          {({ id, describedBy }) => (
            <Input
              id={id}
              type="password"
              autoComplete="new-password"
              aria-describedby={describedBy}
              invalid={!!form.formState.errors.confirmPassword}
              {...form.register('confirmPassword')}
            />
          )}
        </Field>

        <Button type="submit" block loading={reset.isPending}>
          Update password
        </Button>
      </form>
    </div>
  );
}
