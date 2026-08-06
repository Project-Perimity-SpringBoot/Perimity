import { useState } from 'react';
import { useNavigate } from 'react-router';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Button, Field, PasswordInput } from '@ui/index';
import { FormError } from '@components/feedback';
import { authApi } from '@lib/api/services/auth.api';
import { authKeys } from '@lib/query/keys';
import { useAuthStore } from '@stores/authStore';
import { useAuth } from '@hooks/useAuth';
import { useToast } from '@hooks/useToast';
import { LANDING_ROUTE } from '@lib/auth/permissions';
import { changePasswordSchema, type ChangePasswordValues } from '../schemas/auth.schemas';
import { useApiFormErrors } from '@hooks/useApiForm';
import { PasswordRulesList } from './PasswordRulesList';

export default function ChangePasswordPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const queryClient = useQueryClient();
  const setProfile = useAuthStore((s) => s.setProfile);
  const { role, profile } = useAuth();
  const [formErrors, setFormErrors] = useState<string[]>([]);

  const form = useForm<ChangePasswordValues>({
    resolver: zodResolver(changePasswordSchema),
    // Tell the user while their attention is still on the field, not after they
    // press the button. Silent on arrival, live once they start correcting.
    mode: 'onTouched',
    reValidateMode: 'onChange',
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  });
  const applyApiErrors = useApiFormErrors<ChangePasswordValues>(form.setError, setFormErrors);
  const newPassword = form.watch('newPassword');

  const change = useMutation({
    mutationFn: (values: ChangePasswordValues) => authApi.changePassword(values),
    /*
     * Refresh the profile BEFORE navigating, and wait for it.
     *
     * `profile` is a store snapshot taken at sign-in, so after a forced change
     * it still carries mustChangePassword: true. PasswordChangeGate reads that
     * flag and sends every other route back here — so navigating first left the
     * user pinned to this screen with a cleared form and no error, even though
     * the password had in fact been changed. Only a full page reload escaped,
     * because that is what re-runs SessionBootstrap.
     *
     * fetchQuery (not invalidate) because the gate must see the new value on
     * the very next render; an invalidate races the navigation and loses.
     */
    onSuccess: async () => {
      try {
        setProfile(await queryClient.fetchQuery({
          queryKey: authKeys.me(),
          queryFn: () => authApi.me(),
        }));
      } catch {
        // The password DID change. Failing to re-read the profile is not a
        // reason to strand the user here - the gate resolves on next load.
      }
      toast.success('Password changed');
      navigate(role ? LANDING_ROUTE[role] : '/', { replace: true });
    },
    onError: applyApiErrors,
  });

  return (
    <div className="flex flex-col gap-[var(--sp-4)]">
      <div>
        <h1 className="text-h1 text-[var(--ink-900)]">Change your password</h1>
        {profile?.mustChangePassword && (
          <p className="text-small mt-[var(--sp-1)] text-[var(--ink-700)]">
            You are signed in with a temporary password. Choose a new one to continue.
          </p>
        )}
      </div>

      <FormError messages={formErrors} />

      <form
        noValidate
        className="flex flex-col gap-[var(--sp-4)]"
        onSubmit={form.handleSubmit((values) => {
          setFormErrors([]);
          change.mutate(values);
        })}
      >
        <Field label="Current password" required error={form.formState.errors.currentPassword?.message}>
          {({ id, describedBy }) => (
            <PasswordInput
              id={id}
              autoComplete="current-password"
              autoFocus
              aria-describedby={describedBy}
              invalid={!!form.formState.errors.currentPassword}
              {...form.register('currentPassword')}
            />
          )}
        </Field>

        <Field label="New password" required error={form.formState.errors.newPassword?.message}>
          {({ id, describedBy }) => (
            <PasswordInput
              id={id}
              autoComplete="new-password"
              aria-describedby={describedBy}
              invalid={!!form.formState.errors.newPassword}
              {...form.register('newPassword')}
            />
          )}
        </Field>

        <PasswordRulesList value={newPassword} />

        <Field label="Confirm new password" required error={form.formState.errors.confirmPassword?.message}>
          {({ id, describedBy }) => (
            <PasswordInput
              id={id}
              autoComplete="new-password"
              aria-describedby={describedBy}
              invalid={!!form.formState.errors.confirmPassword}
              {...form.register('confirmPassword')}
            />
          )}
        </Field>

        <Button type="submit" block loading={change.isPending}>
          Change password
        </Button>
      </form>
    </div>
  );
}
