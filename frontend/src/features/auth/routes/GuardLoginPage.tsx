import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router';
import { ArrowLeft, ClipboardList, Layers, QrCode, ShieldCheck, Users } from 'lucide-react';
import { Button, Field, Input, PasswordInput } from '@ui/index';
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
 * THE SHELL IS NOW THE SAME AS THE OTHER SIGN-IN SCREENS: a white card on a
 * dark field, laid out here rather than inherited, because this route sits
 * outside AuthLayout and moving it in would mean editing the router.
 *
 * `data-shell="guard"` is gone. It swapped the surface tokens to the scanner's
 * dark treatment, which is right at a gate in sunlight and wrong here — this is
 * the one guard screen reached before a shift starts.
 *
 * The 56px targets stayed. They are on the fields and the button themselves
 * (`min-h-14`), never on the shell, so a gloved thumb has the same target it
 * always had. The dark treatment was about sunlight; the target size is about
 * gloves, and only one of those stopped applying.
 *
 * On success it goes to /guard/session, never to /guard. A guard with no open
 * shift has no gate to log a scan against; GuardSessionGate enforces that too,
 * but arriving at the right screen beats being bounced to it.
 */

/* Written inline, not as a token, so this file needs no companion. Same value
   as AuthLayout — if you ever move one into tokens.css, move both. */
const AUTH_BG = 'linear-gradient(155deg, #1e3a8a 0%, #172554 55%, #0f172a 100%)';
const PANEL_INK = '#c7d2fe';

const FACTS = [
  { icon: Users, value: '6', label: 'User roles' },
  { icon: Layers, value: '6', label: 'Microservices' },
  { icon: QrCode, value: 'QR', label: 'Encrypted passes' },
  { icon: ClipboardList, value: '100%', label: 'Scans audited' },
];

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
      /*
       * GUARDS ONLY, ENFORCED HERE.
       *
       * This screen shares one endpoint with the main sign-in. POST
       * /api/auth/login authenticates by password and does not care which door
       * the request came through, and there is no guard-only login endpoint to
       * point at instead — so the server will happily return a valid FACULTY or
       * STUDENT token to a correct password typed on this page.
       *
       * The check therefore has to happen here, and it has to happen BEFORE
       * completeSignIn: that is the call that writes the token to storage and
       * starts the session. Checking afterwards would mean signing someone in
       * and then undoing it, which is a different and worse bug.
       *
       * Be clear about what this is and is not. It is a door, not a lock. The
       * password was correct and the same person can sign in at /login two
       * seconds later — as they should, because they are a legitimate user.
       * What it prevents is arriving at a screen labelled "Guard sign-in",
       * succeeding, and then being bounced to /forbidden by the RoleRoute on
       * /guard/session. The real enforcement is on the server, where every
       * guard endpoint is hasRole('GUARD').
       *
       * The token the server minted is discarded, not revoked. Nothing here
       * holds it, so there is no Authorization header to call logout with. It
       * expires on its own like any token belonging to a tab that was closed.
       */
      if (auth.user?.role !== 'GUARD') {
        setFormErrors([
          'This entrance is for guards only. Your account is valid — use the main sign-in below.',
        ]);
        return;
      }

      const landing = completeSignIn(auth);
      navigate(auth.mustChangePassword ? landing : '/guard/session', { replace: true });
    },
    onError: (error) => applyApiErrors(error),
  });

  return (
    <div
      className="flex min-h-dvh flex-col items-center justify-center px-[var(--sp-4)] py-[var(--sp-8)]"
      style={{ background: AUTH_BG }}
    >
      <div className="grid w-full max-w-[1100px] items-center gap-[var(--sp-8)] lg:grid-cols-[1fr_460px]">

        {/* Brand panel. Hidden below lg: on a phone it would push the form off
            the fold, and the form is the only reason anyone opens this page. */}
        <div className="hidden text-center lg:block">
          <span className="mx-auto flex size-28 items-center justify-center rounded-[var(--r-lg)] bg-[var(--brand-600)]">
            <ShieldCheck className="size-14 text-white" aria-hidden />
          </span>

          <p className="mt-[var(--sp-6)] text-[length:34px] font-extrabold leading-tight tracking-tight text-white">
            Perimity
          </p>
          <p className="text-body mt-[var(--sp-3)]" style={{ color: PANEL_INK }}>
            Smart Campus Access &mdash; digital gate passes, verified in seconds
          </p>

          <ul className="mx-auto mt-[var(--sp-8)] grid max-w-md grid-cols-2 gap-[var(--sp-4)]">
            {FACTS.map((fact) => (
              <li
                key={fact.label}
                className="rounded-[var(--r-md)] border border-white/10 bg-white/5 p-[var(--sp-4)]"
              >
                <fact.icon className="mx-auto size-5" style={{ color: PANEL_INK }} aria-hidden />
                <p className="text-h1 mt-[var(--sp-2)] text-white">{fact.value}</p>
                <p className="text-small" style={{ color: PANEL_INK }}>{fact.label}</p>
              </li>
            ))}
          </ul>
        </div>

        {/* The card. */}
        <div className="mx-auto w-full max-w-md">
          <div className="surface-card p-[var(--sp-6)] sm:p-[var(--sp-8)]">
            <span className="mx-auto mb-[var(--sp-6)] flex size-16 items-center justify-center rounded-[var(--r-md)] bg-[var(--brand-600)]">
              <ShieldCheck className="size-8 text-white" aria-hidden />
            </span>

            <div className="text-center">
              <h1 className="text-h1 text-[var(--ink-900)]">Guard sign-in</h1>
              <p className="text-small mt-[var(--sp-1)] text-[var(--ink-500)]">
                Perimity gate control
              </p>
            </div>

            <form
              noValidate
              className="mt-[var(--sp-6)] flex flex-col gap-[var(--sp-4)]"
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
                  <PasswordInput
                    id={id}
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
                  A guard resets through their campus administrator, not through
                  an inbox they may not have on the gate phone. */}
              <p className="text-small text-center text-[var(--ink-500)]">
                Forgotten your password? Your campus administrator resets it.
              </p>
            </form>
          </div>

          <Link
            to="/"
            className="text-small mt-[var(--sp-6)] flex min-h-14 items-center justify-center gap-[var(--sp-2)] transition-colors hover:text-white"
            style={{ color: PANEL_INK }}
          >
            <ArrowLeft className="size-4" aria-hidden />
            Back to home
          </Link>

          <div
            className="text-small flex items-center justify-center"
            style={{ color: PANEL_INK }}
          >
            <Link to="/login" className="hover:text-white">Not a guard? Sign in here</Link>
          </div>
        </div>
      </div>

      <p className="text-caption mt-[var(--sp-8)]" style={{ color: PANEL_INK }}>
        Secure digital gate passes for safe campus access
      </p>
    </div>
  );
}
