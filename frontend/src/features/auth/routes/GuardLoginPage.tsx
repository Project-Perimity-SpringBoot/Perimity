import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import { ShieldCheck } from 'lucide-react';
import { Button, Field, Input } from '@ui/index';
import { FormError } from '@components/feedback';
import { authApi } from '@lib/api/services/auth.api';
import { useAuth } from '@hooks/useAuth';
import { useApiFormErrors } from '@hooks/useApiForm';
import { loginSchema, type LoginValues } from '../schemas/auth.schemas';

/**
 * Batch 1 screen 7 — guard sign-in.
 *
 * NO OTP LINK. Anywhere. `Role.canLoginWithOtp()` admits FACULTY, STUDENT and
 * VISITOR only, so a guard tapping "send me a code" would get the same cheerful
 * 200 as everyone else and then wait at a gate for an email that is never
 * coming. The link is absent rather than disabled, because a disabled control
 * still tells them the option exists.
 *
 * `data-shell="guard"` swaps the surface tokens to the dark treatment. This is
 * read one-handed, outdoors, in sunlight — hence 56px targets rather than the
 * 44px used everywhere else, and a much larger type scale than a desk screen
 * would need.
 *
 * On success it goes to /guard/session, never to /guard. A guard with no open
 * shift has no gate to log a scan against; GuardSessionGate enforces that too,
 * but arriving at the right screen beats being bounced to it.
 */
export default function GuardLoginPage() {
  const navigate = useNavigate();
  const { completeSignIn } = useAuth();
  const [formErrors, setFormErrors] = useState<string[]>([]);

  const form = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    mode: 'onTouched',
    reValidateMode: 'onChange',
    defaultValues: { email: '', password: '' },
  });
  const applyApiErrors = useApiFormErrors<LoginValues>(form.setError, setFormErrors);

  const login = useMutation({
    mutationFn: (values: LoginValues) => authApi.login(values),
    onSuccess: (auth) => {
      const landing = completeSignIn(auth);
      navigate(auth.mustChangePassword ? landing : '/guard/session', { replace: true });
    },
    onError: (error) => applyApiErrors(error),
  });

  return (
    <div
      data-shell="guard"
      className="flex min-h-dvh flex-col justify-center bg-[var(--desk)] px-[var(--sp-4)] py-[var(--sp-6)]"
    >
      <div className="mx-auto w-full max-w-md">
        <div className="mb-[var(--sp-6)] flex items-center gap-[var(--sp-3)]">
          <ShieldCheck className="size-8 text-[var(--brand-300)]" aria-hidden />
          <div>
            <h1 className="text-h1 text-[var(--ink-900)]">Guard sign-in</h1>
            <p className="text-body text-[var(--ink-500)]">Perimity gate control</p>
          </div>
        </div>

        <form
          noValidate
          className="flex flex-col gap-[var(--sp-5)] rounded-[var(--r-lg)] bg-[var(--surface)] p-[var(--sp-6)]"
          onSubmit={form.handleSubmit((values) => {
            setFormErrors([]);
            login.mutate(values);
          })}
        >
          <FormError messages={formErrors} />

          <Field label="Email" required error={form.formState.errors.email?.message}>
            {({ id, describedBy }) => (
              <Input
                id={id}
                type="email"
                inputMode="email"
                autoComplete="username"
                autoFocus
                aria-describedby={describedBy}
                invalid={!!form.formState.errors.email}
                className="min-h-14 text-[length:var(--t-h3-size)]"
                {...form.register('email')}
              />
            )}
          </Field>

          <Field label="Password" required error={form.formState.errors.password?.message}>
            {({ id, describedBy }) => (
              <Input
                id={id}
                type="password"
                autoComplete="current-password"
                aria-describedby={describedBy}
                invalid={!!form.formState.errors.password}
                className="min-h-14 text-[length:var(--t-h3-size)]"
                {...form.register('password')}
              />
            )}
          </Field>

          <Button type="submit" size="lg" block loading={login.isPending} className="min-h-14">
            Sign in
          </Button>

          {/* Deliberately no "use a code instead" and no "forgot password".
              A guard resets through their campus administrator, not through an
              inbox they may not have on the gate phone. */}
          <p className="text-small text-center text-[var(--ink-500)]">
            Forgotten your password? Your campus administrator resets it.
          </p>
        </form>
      </div>
    </div>
  );
}
