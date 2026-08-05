import { useEffect, useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router';
import { useMutation } from '@tanstack/react-query';
import { Button } from '@ui/index';
import { FormError, OtpInput } from '@components/feedback';
import { authApi } from '@lib/api/services/auth.api';
import { ApiError } from '@lib/api/errors';
import { OTP_RULES } from '@lib/validation/patterns';
import { config } from '@lib/config';
import { useAuth } from '@hooks/useAuth';
import type { OtpChallengeResponse } from '@/types/auth.types';

interface VerifyState {
  email: string;
  /** Null when the code request failed after the account was created. */
  challenge: OtpChallengeResponse | null;
}

export default function VerifyCodePage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { completeSignIn } = useAuth();
  const state = location.state as VerifyState | null;

  const [code, setCode] = useState('');
  const [formErrors, setFormErrors] = useState<string[]>([]);
  // The server does not return an attempt count, so this is tracked locally to
  // tell the user how many tries remain before the code dies.
  const [attemptsUsed, setAttemptsUsed] = useState(0);
  const [cooldown, setCooldown] = useState<number>(OTP_RULES.resendCooldownSeconds);

  useEffect(() => {
    if (cooldown <= 0) return;
    const id = setTimeout(() => setCooldown((c) => c - 1), 1000);
    return () => clearTimeout(id);
  }, [cooldown]);

  const verify = useMutation({
    mutationFn: (value: string) =>
      authApi.verifyOtp({ email: state!.email, purpose: 'LOGIN', code: value }),
    onSuccess: (auth) => navigate(completeSignIn(auth), { replace: true }),
    onError: (error) => {
      setAttemptsUsed((n) => n + 1);
      setCode('');
      setFormErrors([error instanceof ApiError ? error.message : 'That code is not correct.']);
    },
  });

  const resend = useMutation({
    mutationFn: () =>
      authApi.requestOtp({ email: state!.email, purpose: 'LOGIN', campusId: config.defaultCampusId }),
    onSuccess: () => {
      setCooldown(OTP_RULES.resendCooldownSeconds);
      setAttemptsUsed(0);
      setFormErrors([]);
    },
  });

  if (!state?.email) return <Navigate to="/login/code" replace />;

  const remaining = Math.max(0, OTP_RULES.maxAttempts - attemptsUsed);

  return (
    <div className="flex flex-col gap-[var(--sp-4)]">
      <div>
        <h1 className="text-h1 text-[var(--ink-900)]">Enter your code</h1>
        <p className="text-small mt-[var(--sp-1)] text-[var(--ink-500)]">
          Sent to {state.challenge?.maskedEmail ?? state.email}. It expires in{' '}
          {OTP_RULES.expiryMinutes} minutes.
        </p>
      </div>

      <FormError messages={formErrors} />

      {/*
        * No onComplete. The sixth digit used to submit the code the instant it
        * landed, so a visitor who mistyped one digit had the attempt spent
        * before they could look at what they had typed - and the boxes were
        * disabled while that request was in flight, so they could not correct
        * it either. Both together made a typo unrecoverable without reloading,
        * which costs the code as well.
        *
        * The Verify button below is now the only way to submit. Typing all six
        * digits does nothing until the visitor says so.
        */}
      <OtpInput
        value={code}
        onChange={setCode}
        invalid={formErrors.length > 0}
        disabled={remaining === 0}
        autoFocus
      />

      <p className="text-caption text-[var(--ink-500)]" aria-live="polite">
        {remaining === 0
          ? 'This code has been tried too many times. Request a new one.'
          : `${remaining} of ${OTP_RULES.maxAttempts} attempts remaining`}
      </p>

      <Button
        block
        loading={verify.isPending}
        disabled={code.length !== OTP_RULES.length || remaining === 0}
        onClick={() => verify.mutate(code)}
      >
        Verify and sign in
      </Button>

      <Button
        variant="ghost"
        block
        disabled={cooldown > 0 || resend.isPending}
        onClick={() => resend.mutate()}
      >
        {cooldown > 0 ? `Resend in 0:${String(cooldown).padStart(2, '0')}` : 'Resend code'}
      </Button>
    </div>
  );
}
