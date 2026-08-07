import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Link } from 'react-router';
import { Building2, Key, UserPlus } from 'lucide-react';
import {
  Button, Field, Input, NativeSelect, Textarea,
} from '@ui/index';
import { PageHeader, SectionHeader } from '@components/data';
import { Alert, ErrorState, FormError } from '@components/feedback';
import { authApi } from '@lib/api/services/auth.api';
import { departmentApi, studentApi } from '@lib/api/services/user.api';
import { authKeys, departmentKeys, profileKeys } from '@lib/query/keys';
import { useApiFormErrors } from '@hooks/useApiForm';
import { useAuth } from '@hooks/useAuth';
import { useToast } from '@hooks/useToast';
import { addStudentSchema, type AddStudentValues } from '../schemas/faculty.schemas';

export default function AddStudentPage() {
  const { identity } = useAuth();
  const toast = useToast();
  const queryClient = useQueryClient();

  const [formErrors, setFormErrors] = useState<string[]>([]);
  const [orphanedUserId, setOrphanedUserId] = useState<number | null>(null);

  const departments = useQuery({
    queryKey: departmentKeys.list(identity?.campusId ?? undefined, true),
    queryFn: () => departmentApi.list(identity?.campusId ?? undefined, true),
    enabled: identity?.campusId != null,
  });

  const form = useForm<AddStudentValues>({
    resolver: zodResolver(addStudentSchema),
    /*
     * Validate on blur, then live once a field has been corrected.
     *
     * react-hook-form defaults to onSubmit, so every rule on this page stayed
     * silent while it was being filled in - a phone number could be typed to
     * any length and looked accepted until submit. Blur is the moment the
     * person has finished with the field and before they have moved on.
     */
    mode: 'onTouched',
    reValidateMode: 'onChange',
    defaultValues: {
      name: '', email: '', phone: '', temporaryPassword: '',
      departmentId: '', rollNo: '', govId: '', address: '',
    },
  });
  const applyApiErrors = useApiFormErrors<AddStudentValues>(form.setError, setFormErrors);

  const create = useMutation({
    mutationFn: async (values: AddStudentValues) => {
      const account = await authApi.createUser({
        email: values.email,
        name: values.name,
        ...(values.phone ? { phone: values.phone } : {}),
        role: 'STUDENT',
        campusId: identity?.campusId as number,
        temporaryPassword: values.temporaryPassword,
      });

      try {
        await studentApi.create({
          userId: account.id,
          campusId: identity?.campusId as number,
          departmentId: values.departmentId === '' ? null : Number(values.departmentId),
          ...(values.rollNo ? { rollNo: values.rollNo } : {}),
          ...(values.govId ? { govId: values.govId } : {}),
          ...(values.address ? { address: values.address } : {}),
        });
      } catch (profileError) {
        setOrphanedUserId(account.id);
        throw profileError;
      }

      return account;
    },
    onSuccess: (account) => {
      void queryClient.invalidateQueries({ queryKey: authKeys.users() });
      void queryClient.invalidateQueries({ queryKey: profileKeys.all });
      toast.success(`${account.name} added — they must change the password at first sign-in`);
      setOrphanedUserId(null);
      setFormErrors([]);
      form.reset();
    },
    onError: (error) => { setFormErrors([]); applyApiErrors(error); },
  });

  if (identity?.campusId == null) {
    return (
      <ErrorState
        error={new Error('Your account has no campus, so a student cannot be enrolled against one.')}
        onRetry={() => window.location.reload()}
      />
    );
  }

  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      <PageHeader
        breadcrumbs={[{ label: 'Faculty', to: '/faculty' }, { label: 'Add a student' }]}
        title="Add a student"
        description="Register one student account. For a whole cohort, use bulk onboarding instead."
        actions={
          <Button variant="secondary" asChild>
            <Link to="/faculty/students/import">Bulk onboarding</Link>
          </Button>
        }
      />

      {orphanedUserId !== null && (
        <Alert tone="warning" title="Account created, profile pending">
          User #{orphanedUserId} exists but has no department profile. Do not re-submit this
          form — ask a Campus Admin to assign profile data against that ID.
        </Alert>
      )}

      <form
        noValidate
        onSubmit={form.handleSubmit((values) => create.mutate(values))}
        className="surface-panel flex max-w-3xl flex-col gap-[var(--sp-6)] p-[var(--sp-6)]"
      >
        <FormError messages={formErrors} />

        <fieldset className="flex flex-col gap-[var(--sp-4)]">
          <SectionHeader
            icon={Key}
            title="Account credentials"
            description="The student's sign-in identity across the campus services."
            divided
          />

          <Field label="Full name" required error={form.formState.errors.name?.message}>
            {({ id, describedBy }) => (
              <Input
                id={id}
                aria-describedby={describedBy}
                placeholder="Ayaan Bhat"
                invalid={Boolean(form.formState.errors.name)}
                {...form.register('name')}
              />
            )}
          </Field>

          <div className="grid gap-[var(--sp-4)] sm:grid-cols-2">
            <Field label="Email" required error={form.formState.errors.email?.message}>
              {({ id, describedBy }) => (
                <Input
                  id={id}
                  type="email"
                  autoComplete="off"
                  placeholder="ayaan.bhat@student.edu"
                  aria-describedby={describedBy}
                  invalid={Boolean(form.formState.errors.email)}
                  {...form.register('email')}
                />
              )}
            </Field>

            <Field label="Phone" hint="Optional. 10 digits only." error={form.formState.errors.phone?.message}>
              {({ id, describedBy }) => (
                <Input
                  id={id}
                  type="tel"
                  placeholder="9876543210"
                  maxLength={10}
                  inputMode="numeric"
                  aria-describedby={describedBy}
                  invalid={Boolean(form.formState.errors.phone)}
                  {...form.register('phone', {
                    onChange: (e) => {
                      const digits = e.target.value.replace(/\D/g, '').slice(0, 10);
                      form.setValue('phone', digits, { shouldValidate: true });
                    },
                  })}
                />
              )}
            </Field>
          </div>

          <Field
            label="Temporary password"
            required
            hint="At least 8 characters with an uppercase, a lowercase and a number. Changed at first sign-in."
            error={form.formState.errors.temporaryPassword?.message}
          >
            {({ id, describedBy }) => (
              <Input
                id={id}
                type="text"
                autoComplete="off"
                mono
                placeholder="TempPass123!"
                aria-describedby={describedBy}
                invalid={Boolean(form.formState.errors.temporaryPassword)}
                {...form.register('temporaryPassword')}
              />
            )}
          </Field>
        </fieldset>

        <fieldset className="flex flex-col gap-[var(--sp-4)]">
          <SectionHeader
            icon={Building2}
            title="Academic profile"
            description="Campus details checked when their gate pass is verified."
            divided
          />

          <div className="grid gap-[var(--sp-4)] sm:grid-cols-2">
            <Field label="Department" error={form.formState.errors.departmentId?.message}>
              {({ id, describedBy }) => (
                <NativeSelect id={id} aria-describedby={describedBy} {...form.register('departmentId')}>
                  <option value="">Select a department</option>
                  {(departments.data ?? []).map((d) => (
                    <option key={d.id} value={d.id}>{d.name}</option>
                  ))}
                </NativeSelect>
              )}
            </Field>

            <Field
              label="Roll number"
              hint="The format used by their department."
              error={form.formState.errors.rollNo?.message}
            >
              {({ id, describedBy }) => (
                <Input
                  id={id}
                  placeholder="IT2026001"
                  aria-describedby={describedBy}
                  invalid={Boolean(form.formState.errors.rollNo)}
                  {...form.register('rollNo')}
                />
              )}
            </Field>
          </div>

          <Field
            label="Government ID"
            hint="12 digits. Stored securely and masked on shared views."
            error={form.formState.errors.govId?.message}
          >
            {({ id, describedBy }) => (
              <Input
                id={id}
                inputMode="numeric"
                autoComplete="off"
                mono
                placeholder="1234 5678 9012"
                aria-describedby={describedBy}
                invalid={Boolean(form.formState.errors.govId)}
                {...form.register('govId')}
              />
            )}
          </Field>

          <Field label="Address" error={form.formState.errors.address?.message}>
            {({ id, describedBy }) => (
              <Textarea
                id={id}
                rows={3}
                aria-describedby={describedBy}
                placeholder="Full residential address"
                invalid={Boolean(form.formState.errors.address)}
                {...form.register('address')}
              />
            )}
          </Field>
        </fieldset>

        <div className="flex items-center justify-end gap-[var(--sp-2)] border-t border-[var(--border)] pt-[var(--sp-4)]">
          <Button type="button" variant="ghost" onClick={() => form.reset()}>
            Reset
          </Button>
          <Button type="submit" loading={create.isPending}>
            <UserPlus aria-hidden /> Create account
          </Button>
        </div>
      </form>
    </div>
  );
}
