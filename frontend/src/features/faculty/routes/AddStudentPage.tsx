import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Link } from 'react-router';
import { AlertTriangle, ArrowLeft, UserPlus, UserCheck, Key, Building2 } from 'lucide-react';
import {
  Button, Field, Input, NativeSelect, Textarea,
} from '@ui/index';
import { ErrorState, FormError } from '@components/feedback';
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
    <div className="mx-auto max-w-4xl space-y-6 pb-16">
      <Button variant="ghost" size="sm" asChild className="gap-2 text-slate-600 hover:text-slate-900">
        <Link to="/faculty"><ArrowLeft className="size-4" /> Back to overview</Link>
      </Button>

      {/* Hero Header */}
      <div className="relative overflow-hidden rounded-3xl border border-indigo-100 bg-gradient-to-r from-slate-900 via-indigo-950 to-slate-900 p-8 text-white shadow-xl">
        <div className="flex flex-wrap items-center justify-between gap-6">
          <div className="space-y-2">
            <div className="inline-flex items-center gap-2 rounded-full bg-white/10 px-3 py-1 text-xs font-semibold text-indigo-200 backdrop-blur-md border border-white/10">
              <UserCheck className="size-3.5 text-indigo-300" /> Direct Enrollment
            </div>
            <h1 className="text-3xl font-extrabold text-white sm:text-4xl">Add Student Account</h1>
            <p className="text-sm text-indigo-200/90 max-w-xl">
              Register a single student account directly. For whole cohorts, use the bulk onboarding tool.
            </p>
          </div>

          <Button variant="secondary" asChild className="bg-white/10 text-white border-white/10 hover:bg-white/20">
            <Link to="/faculty/students/import">Bulk Onboarding</Link>
          </Button>
        </div>
      </div>

      {orphanedUserId !== null && (
        <div className="flex items-start gap-3 rounded-2xl border border-amber-200 bg-amber-50 p-5 text-amber-900">
          <AlertTriangle className="size-5 shrink-0 text-amber-600 mt-0.5" />
          <div className="text-xs space-y-1">
            <h2 className="font-bold text-sm">Account created, profile pending</h2>
            <p>
              User <span className="font-mono font-bold">#{orphanedUserId}</span> account exists but has no department profile. Do not re-submit this form. Ask a Campus Admin to assign profile data against ID #{orphanedUserId}.
            </p>
          </div>
        </div>
      )}

      {/* Form Container */}
      <form
        noValidate
        onSubmit={form.handleSubmit((values) => create.mutate(values))}
        className="rounded-3xl border border-slate-200/80 bg-white p-8 shadow-sm space-y-8"
      >
        <FormError messages={formErrors} />

        {/* Section 1: Sign-in details */}
        <div className="space-y-4">
          <div className="flex items-center gap-2.5 border-b border-slate-100 pb-3">
            <Key className="size-5 text-indigo-600" />
            <div>
              <h2 className="font-bold text-slate-900 text-base">Account Credentials</h2>
              <p className="text-xs text-slate-500">Student login identity across all campus microservices.</p>
            </div>
          </div>

          <Field label="Full Name" required error={form.formState.errors.name?.message}>
            {({ id, describedBy }) => (
              <Input
                id={id}
                aria-describedby={describedBy}
                placeholder="e.g. Ayaan Bhat"
                invalid={Boolean(form.formState.errors.name)}
                {...form.register('name')}
                className="bg-white"
              />
            )}
          </Field>

          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Email Address" required error={form.formState.errors.email?.message}>
              {({ id, describedBy }) => (
                <Input
                  id={id}
                  type="email"
                  autoComplete="off"
                  placeholder="ayaan.bhat@student.edu"
                  aria-describedby={describedBy}
                  invalid={Boolean(form.formState.errors.email)}
                  {...form.register('email')}
                  className="bg-white"
                />
              )}
            </Field>

            <Field label="Phone Number" error={form.formState.errors.phone?.message}>
              {({ id, describedBy }) => (
                <Input
                  id={id}
                  type="tel"
                  placeholder="+919876543210"
                  aria-describedby={describedBy}
                  invalid={Boolean(form.formState.errors.phone)}
                  {...form.register('phone')}
                  className="bg-white"
                />
              )}
            </Field>
          </div>

          <Field
            label="Temporary Password"
            required
            hint="At least 8 characters (1 uppercase, 1 lowercase, 1 number). Student changes this at first sign-in."
            error={form.formState.errors.temporaryPassword?.message}
          >
            {({ id, describedBy }) => (
              <Input
                id={id}
                type="text"
                autoComplete="off"
                placeholder="TempPass123!"
                aria-describedby={describedBy}
                invalid={Boolean(form.formState.errors.temporaryPassword)}
                {...form.register('temporaryPassword')}
                className="bg-white font-mono"
              />
            )}
          </Field>
        </div>

        {/* Section 2: Student Profile details */}
        <div className="space-y-4 pt-2">
          <div className="flex items-center gap-2.5 border-b border-slate-100 pb-3">
            <Building2 className="size-5 text-indigo-600" />
            <div>
              <h2 className="font-bold text-slate-900 text-base">Academic Profile Data</h2>
              <p className="text-xs text-slate-500">Campus details for gate pass verification.</p>
            </div>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Department" error={form.formState.errors.departmentId?.message}>
              {({ id, describedBy }) => (
                <NativeSelect id={id} aria-describedby={describedBy} {...form.register('departmentId')} className="bg-white">
                  <option value="">Select Department</option>
                  {(departments.data ?? []).map((d) => (
                    <option key={d.id} value={d.id}>{d.name}</option>
                  ))}
                </NativeSelect>
              )}
            </Field>

            <Field
              label="Roll Number"
              hint="Format used by your department."
              error={form.formState.errors.rollNo?.message}
            >
              {({ id, describedBy }) => (
                <Input
                  id={id}
                  placeholder="IT2026001"
                  aria-describedby={describedBy}
                  invalid={Boolean(form.formState.errors.rollNo)}
                  {...form.register('rollNo')}
                  className="bg-white"
                />
              )}
            </Field>
          </div>

          <Field
            label="Government ID / Aadhaar"
            hint="12 digits. Stored securely and masked on public views."
            error={form.formState.errors.govId?.message}
          >
            {({ id, describedBy }) => (
              <Input
                id={id}
                inputMode="numeric"
                autoComplete="off"
                placeholder="1234 5678 9012"
                aria-describedby={describedBy}
                invalid={Boolean(form.formState.errors.govId)}
                {...form.register('govId')}
                className="bg-white font-mono"
              />
            )}
          </Field>

          <Field label="Address" error={form.formState.errors.address?.message}>
            {({ id, describedBy }) => (
              <Textarea
                id={id}
                rows={3}
                aria-describedby={describedBy}
                placeholder="Full residence address..."
                invalid={Boolean(form.formState.errors.address)}
                {...form.register('address')}
                className="bg-white"
              />
            )}
          </Field>
        </div>

        <div className="flex items-center gap-3 border-t border-slate-100 pt-6">
          <Button
            type="submit"
            loading={create.isPending}
            className="gap-2 bg-indigo-600 text-white font-semibold shadow-md shadow-indigo-500/20 px-6 hover:bg-indigo-700"
          >
            <UserPlus className="size-4" /> Create Student Account
          </Button>
          <Button type="button" variant="ghost" onClick={() => form.reset()}>
            Reset Form
          </Button>
        </div>
      </form>
    </div>
  );
}
