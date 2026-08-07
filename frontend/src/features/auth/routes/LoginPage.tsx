import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { Button, Field, Input, PasswordInput } from '@ui/index';
import { FormError } from '@components/feedback';
import {
  AccountLockedNotice, SessionExpiredNotice, isLockoutMessage,
} from './AccountStatusNotice';
import { authApi } from '@lib/api/services/auth.api';
import { RateLimitError } from '@lib/api/errors';
import { useAuth } from '@hooks/useAuth';
import { loginSchema, type LoginValues } from '../schemas/auth.schemas';
import { useApiFormErrors } from '@hooks/useApiForm';

export default function LoginPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const { completeSignIn } = useAuth();
  const [formErrors, setFormErrors] = useState<string[]>([]);
  const [lockedMessage, setLockedMessage] = useState<string | null>(null);
  const [retryAfter, setRetryAfter] = useState<number | null>(null);

  const form = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    // Tell the user while their attention is still on the field, not after they
    // press the button. Silent on arrival, live once they start correcting.
    mode: 'onTouched',
    reValidateMode: 'onChange',
    defaultValues: { email: '', password: '' },
  });
  const applyApiErrors = useApiFormErrors<LoginValues>(form.setError, setFormErrors);

  const login = useMutation({
    mutationFn: (values: LoginValues) => authApi.login(values),
    onSuccess: (auth) => {
      const next = params.get('next');
      navigate(next ?? completeSignIn(auth), { replace: true });
    },
    onError: (error) => {
      if (error instanceof RateLimitError) setRetryAfter(error.retryAfterSeconds ?? 60);

      // A lockout and a wrong password are both 401s. Only the lockout carries
      // an unlock time, and it deserves its own panel — "invalid credentials"
      // when the real answer is "come back at 14:05" just makes people retry.
      const message = error instanceof Error ? error.message : '';
      if (isLockoutMessage(message)) {
        setLockedMessage(message);
        setFormErrors([]);
        return;
      }
      setLockedMessage(null);
      applyApiErrors(error);
    },
  });

  const expired = params.get('reason') === 'expired';

  return (
    <div className="flex flex-col gap-[var(--sp-4)]">
      <div>
        <h1 className="text-h1 text-[var(--ink-900)]">Sign in</h1>
        <p className="text-small mt-[var(--sp-1)] text-[var(--ink-500)]">
          For students, faculty, administrators and guards.
        </p>
      </div>

      {expired && !lockedMessage ? <SessionExpiredNotice /> : null}

      {lockedMessage ? (
        <AccountLockedNotice message={lockedMessage} />
      ) : (
        <FormError messages={formErrors} />
      )}

      <form
        noValidate
        className="flex flex-col gap-[var(--sp-4)]"
        onSubmit={form.handleSubmit((values) => {
          setFormErrors([]);
          setLockedMessage(null);
          login.mutate(values);
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

        <Field label="Password" required error={form.formState.errors.password?.message}>
          {({ id, describedBy }) => (
            <PasswordInput
              id={id}
              autoComplete="current-password"
              aria-describedby={describedBy}
              invalid={!!form.formState.errors.password}
              {...form.register('password')}
            />
          )}
        </Field>

        <Button type="submit" block loading={login.isPending} disabled={retryAfter !== null}>
          {retryAfter !== null ? `Try again in ${retryAfter}s` : 'Sign in'}
        </Button>
      </form>

      <div className="flex flex-col gap-[var(--sp-2)] border-t border-[var(--border)] pt-[var(--sp-4)] text-small">
        <Link to="/login/code" className="text-[var(--brand-600)] hover:underline">
          Sign in with an email code instead
        </Link>
        <p className="text-caption text-[var(--ink-500)]">
          Students, faculty and visitors can sign in with a code. Administrators and guards must
          use a password.
        </p>
        <Link to="/forgot-password" className="text-[var(--brand-600)] hover:underline">
          Forgot your password?
        </Link>
      </div>
    </div>
  );
}
