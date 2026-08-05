import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Link } from 'react-router';
import { AlertTriangle, ArrowLeft, UserPlus } from 'lucide-react';
import {
  Button, Field, Input, NativeSelect, Textarea,
} from '@ui/index';
import { ErrorState, FormError } from '@components/feedback';
import { PageHeader } from '@components/data';
import { authApi } from '@lib/api/services/auth.api';
import { departmentApi, studentApi } from '@lib/api/services/user.api';
import { authKeys, departmentKeys, profileKeys } from '@lib/query/keys';
import { useApiFormErrors } from '@hooks/useApiForm';
import { useAuth } from '@hooks/useAuth';
import { useToast } from '@hooks/useToast';
import { addStudentSchema, type AddStudentValues } from '../schemas/faculty.schemas';

/**
 * Add one student, by hand.
 *
 * ==========================================================================
 * WHY THIS SCREEN HAD TO EXIST
 * ==========================================================================
 * Faculty are the only role permitted to create a STUDENT account -
 * UserAdminController.CREATABLE says so, on the reasoning that a campus admin
 * does not know who is in which class. But the only student-creating UI was
 * bulk onboarding, so the single-student path the policy assumes had no screen
 * at all. A rule with no way to obey it is a rule nobody can follow.
 *
 * Bulk upload stays the right tool for a cohort. This is for the one student who
 * joined late, whose row failed validation, or whose details were wrong.
 *
 * ==========================================================================
 * A STUDENT IS TWO RECORDS, AND THE SECOND ONE CAN FAIL
 * ==========================================================================
 * The login account lives in auth-service; the identity profile - department,
 * roll number, government ID - lives in user-service. There is no transaction
 * across the two, and there cannot be one.
 *
 * So the interesting case is a partial success: account created, profile not.
 * That leaves a student who can sign in and has no department. The screen does
 * NOT pretend this is a clean failure - it says exactly what exists, gives the
 * user id, and tells the person what to do about it. Silently showing "could
 * not create student" after creating half of one is how a duplicate account
 * gets made on the retry.
 *
 * The order is deliberate: account first, because the profile needs the userId
 * the account create returns. The reverse is not possible.
 */
export default function AddStudentPage() {
  const { identity } = useAuth();
  const toast = useToast();
  const queryClient = useQueryClient();

  const [formErrors, setFormErrors] = useState<string[]>([]);
  /** Set only when the account was created but the profile was not. */
  const [orphanedUserId, setOrphanedUserId] = useState<number | null>(null);

  const departments = useQuery({
    queryKey: departmentKeys.list(identity?.campusId ?? undefined, true),
    queryFn: () => departmentApi.list(identity?.campusId ?? undefined, true),
    enabled: identity?.campusId != null,
  });

  const form = useForm<AddStudentValues>({
    resolver: zodResolver(addStudentSchema),
    defaultValues: {
      name: '', email: '', phone: '', temporaryPassword: '',
      departmentId: '', rollNo: '', govId: '', address: '',
    },
  });
  const applyApiErrors = useApiFormErrors<AddStudentValues>(form.setError, setFormErrors);

  const create = useMutation({
    mutationFn: async (values: AddStudentValues) => {
      // Step 1 - the login account. campusId comes from the token, never a
      // field: a faculty member must not be able to enrol someone at another
      // campus by typing a different number.
      const account = await authApi.createUser({
        email: values.email,
        name: values.name,
        ...(values.phone ? { phone: values.phone } : {}),
        role: 'STUDENT',
        campusId: identity?.campusId as number,
        temporaryPassword: values.temporaryPassword,
      });

      // Step 2 - the identity profile. If THIS throws, the account above still
      // exists; the catch re-labels the error so the screen can say so rather
      // than reporting a failure that only half happened.
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
      <Button variant="link" asChild className="self-start">
        <Link to="/faculty"><ArrowLeft aria-hidden />Back to overview</Link>
      </Button>

      <PageHeader
        title="Add a student"
        description="For one student. Use bulk onboarding for a whole cohort."
        actions={
          <Button variant="secondary" asChild>
            <Link to="/faculty/onboarding">Bulk onboarding</Link>
          </Button>
        }
      />

      {/* The partial-success case. Loud, specific, and it names the id so the
          account can be finished rather than duplicated. */}
      {orphanedUserId !== null && (
        <section
          role="alert"
          className="flex items-start gap-[var(--sp-3)] rounded-[var(--r-md)]
                     border border-[var(--status-border)] bg-[var(--status-bg)] p-[var(--sp-4)]"
        >
          <AlertTriangle aria-hidden className="mt-[2px] size-5 shrink-0 text-[var(--ink-700)]" />
          <div>
            <h2 className="text-body-md text-[var(--ink-900)]">
              The account was created, but the profile was not
            </h2>
            <p className="text-small mt-[var(--sp-1)] text-[var(--ink-700)]">
              User <span className="text-mono">#{orphanedUserId}</span> can sign in but has no
              department, roll number or government ID. Do <strong>not</strong> submit this form
              again — that would create a second account on a different email or fail on a duplicate.
              Ask a Campus Admin to complete the profile against that user id.
            </p>
          </div>
        </section>
      )}

      <form
        noValidate
        onSubmit={form.handleSubmit((values) => create.mutate(values))}
        className="surface-card flex flex-col gap-[var(--sp-4)] p-[var(--sp-6)]"
      >
        <FormError messages={formErrors} />

        <div>
          <h2 className="text-h3 text-[var(--ink-900)]">Sign-in details</h2>
          <p className="text-caption text-[var(--ink-500)]">
            Email is their identity across every service and cannot be changed later.
          </p>
        </div>

        <Field label="Full name" required error={form.formState.errors.name?.message}>
          {({ id, describedBy }) => (
            <Input id={id} aria-describedby={describedBy}
                   invalid={Boolean(form.formState.errors.name)} {...form.register('name')} />
          )}
        </Field>

        <div className="grid gap-[var(--sp-4)] sm:grid-cols-2">
          <Field label="Email" required error={form.formState.errors.email?.message}>
            {({ id, describedBy }) => (
              <Input id={id} type="email" autoComplete="off" aria-describedby={describedBy}
                     invalid={Boolean(form.formState.errors.email)} {...form.register('email')} />
            )}
          </Field>

          <Field label="Phone" error={form.formState.errors.phone?.message}>
            {({ id, describedBy }) => (
              <Input id={id} type="tel" placeholder="+919876543210" aria-describedby={describedBy}
                     invalid={Boolean(form.formState.errors.phone)} {...form.register('phone')} />
            )}
          </Field>
        </div>

        <Field
          label="Temporary password"
          required
          hint="At least 8 characters with an uppercase letter, a lowercase letter and a number. They must change it at first sign-in."
          error={form.formState.errors.temporaryPassword?.message}
        >
          {({ id, describedBy }) => (
            <Input id={id} type="text" autoComplete="off" aria-describedby={describedBy}
                   invalid={Boolean(form.formState.errors.temporaryPassword)}
                   {...form.register('temporaryPassword')} />
          )}
        </Field>

        <div className="mt-[var(--sp-2)]">
          <h2 className="text-h3 text-[var(--ink-900)]">Student details</h2>
          <p className="text-caption text-[var(--ink-500)]">
            All optional. They can be filled in later from the student&rsquo;s profile.
          </p>
        </div>

        <div className="grid gap-[var(--sp-4)] sm:grid-cols-2">
          <Field label="Department" error={form.formState.errors.departmentId?.message}>
            {({ id, describedBy }) => (
              <NativeSelect id={id} aria-describedby={describedBy} {...form.register('departmentId')}>
                <option value="">Not set</option>
                {(departments.data ?? []).map((d) => (
                  <option key={d.id} value={d.id}>{d.name}</option>
                ))}
              </NativeSelect>
            )}
          </Field>

          <Field
            label="Roll number"
            hint="Whatever format this campus uses."
            error={form.formState.errors.rollNo?.message}
          >
            {({ id, describedBy }) => (
              <Input id={id} placeholder="2026/CS/0141" aria-describedby={describedBy}
                     invalid={Boolean(form.formState.errors.rollNo)} {...form.register('rollNo')} />
            )}
          </Field>
        </div>

        <Field
          label="Government ID"
          hint="12 digits. Stored securely and only ever shown masked."
          error={form.formState.errors.govId?.message}
        >
          {({ id, describedBy }) => (
            <Input id={id} inputMode="numeric" autoComplete="off" aria-describedby={describedBy}
                   invalid={Boolean(form.formState.errors.govId)} {...form.register('govId')} />
          )}
        </Field>

        <Field label="Address" error={form.formState.errors.address?.message}>
          {({ id, describedBy }) => (
            <Textarea id={id} rows={3} aria-describedby={describedBy}
                      invalid={Boolean(form.formState.errors.address)} {...form.register('address')} />
          )}
        </Field>

        <div className="flex flex-wrap gap-[var(--sp-3)]">
          <Button type="submit" loading={create.isPending}>
            <UserPlus aria-hidden />Add student
          </Button>
          <Button type="button" variant="secondary" onClick={() => form.reset()}>
            Clear
          </Button>
        </div>
      </form>

      <p className="text-caption text-[var(--ink-500)]">
        Creating a student makes two records — the sign-in account and the student profile.
        The campus is taken from your own account and is not a field on this form.
      </p>
    </div>
  );
}
