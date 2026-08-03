import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import { Button, Field, Input, NativeSelect, Textarea } from '@ui/index';
import { PageHeader } from '@components/data';
import { ErrorState, FormError } from '@components/feedback';
import { visitorRequestApi } from '@lib/api/services/gatepass.api';
import { facultyApi } from '@lib/api/services/user.api';
import { profileKeys, requestKeys } from '@lib/query/keys';
import { LIMITS } from '@lib/validation/patterns';
import { useApiFormErrors } from '@hooks/useApiForm';
import { useAuth } from '@hooks/useAuth';
import type { FacultyProfileResponse } from '@/types/user.types';
import { visitorRequestSchema, type VisitorRequestValues } from '../schemas/visitor.schemas';

/**
 * Phase 5 screen 2 — apply for a visitor pass.
 *
 * Name, email, phone, purpose, host, from, to. No semester — a visitor has
 * none. No document upload — nobody has agreed to meet this person yet, and
 * asking a stranger for ID before that is friction that buys nothing. No
 * password, on this or any visitor screen.
 *
 * The email is read-only, prefilled from the token. VisitorRequestCreateDto
 * marks it @NotBlank so it must be in the body, but the visitor has already
 * verified this address by OTP — letting them retype it would only allow a
 * mismatch between who is signed in and who the request names.
 *
 * ==========================================================================
 * BLOCKER: THE HOST PICKER CANNOT SHOW NAMES
 * ==========================================================================
 * GET /api/user/faculty is the right endpoint and is correctly readable by any
 * signed-in user — its own Javadoc says "visitors pick a host from it". But
 * FacultyProfileResponse carries no name: only userId, departmentName,
 * designation and employeeId.
 *
 * So a visitor who knows they are meeting Dr. Rao in Computer Science can
 * narrow to the department and the designation, and then has to guess. The
 * field says so rather than pretending the list is complete.
 *
 * Fixing it is one field on the DTO in user-service. Raised with Mukul.
 */
export default function ApplyPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { identity } = useAuth();
  const [formErrors, setFormErrors] = useState<string[]>([]);

  const hosts = useQuery({
    queryKey: profileKeys.facultyList({ page: 0, size: 100 }),
    queryFn: () => facultyApi.list({ page: 0, size: 100 }),
  });

  const {
    register, handleSubmit, setError, formState: { errors },
  } = useForm<VisitorRequestValues>({
    resolver: zodResolver(visitorRequestSchema),
    defaultValues: {
      visitorName: '', visitorEmail: identity?.email ?? '', visitorPhone: '', purpose: '',
      hostUserId: undefined, visitFrom: '', visitTo: '',
    },
  });
  const applyApiErrors = useApiFormErrors<VisitorRequestValues>(setError, setFormErrors);

  const submit = useMutation({
    mutationFn: (values: VisitorRequestValues) =>
      visitorRequestApi.submit({
        visitorName: values.visitorName,
        visitorEmail: values.visitorEmail,
        // Empty string would store a blank phone rather than none.
        visitorPhone: values.visitorPhone ? values.visitorPhone : null,
        purpose: values.purpose,
        hostUserId: values.hostUserId,
        visitFrom: values.visitFrom,
        visitTo: values.visitTo,
        // campusId is @JsonIgnore server-side. Not sent.
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: requestKeys.all });
      navigate('/visitor/submitted');
    },
    onError: (error) => { setFormErrors([]); applyApiErrors(error); },
  });

  if (hosts.isError) {
    return <ErrorState error={hosts.error} onRetry={() => void hosts.refetch()} />;
  }

  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      <PageHeader
        title="Apply for a visitor pass"
        description="Your host reviews this. You are emailed either way, usually the same day."
      />

      <form
        noValidate
        onSubmit={handleSubmit((values) => submit.mutate(values))}
        className="surface-card flex flex-col gap-[var(--sp-5)] p-[var(--sp-6)]"
      >
        <FormError messages={formErrors} />

        <Field label="Full name" required error={errors.visitorName?.message}>
          {({ id, describedBy }) => (
            <Input
              id={id} aria-describedby={describedBy} autoComplete="name"
              invalid={Boolean(errors.visitorName)}
              maxLength={LIMITS.personName.max}
              {...register('visitorName')}
            />
          )}
        </Field>

        {/* Read-only: this address was verified by OTP to get in here. Letting
            it be retyped would only allow the request to name somebody other
            than the person signed in. */}
        <Field
          label="Email"
          hint="The address you signed in with. Your pass is emailed here."
          error={errors.visitorEmail?.message}
        >
          {({ id, describedBy }) => (
            <Input
              id={id} type="email" aria-describedby={describedBy} readOnly
              invalid={Boolean(errors.visitorEmail)}
              {...register('visitorEmail')}
            />
          )}
        </Field>

        <Field
          label="Phone"
          hint="Optional. Useful if your host needs to reach you on the day."
          error={errors.visitorPhone?.message}
        >
          {({ id, describedBy }) => (
            <Input
              id={id} type="tel" aria-describedby={describedBy} autoComplete="tel"
              invalid={Boolean(errors.visitorPhone)}
              placeholder="+919876543210"
              {...register('visitorPhone')}
            />
          )}
        </Field>

        <Field
          label="Who are you visiting"
          required
          hint="Names are not available on this list yet — pick by department and designation."
          error={errors.hostUserId?.message}
        >
          {({ id, describedBy }) => (
            <NativeSelect
              id={id} aria-describedby={describedBy}
              disabled={hosts.isPending}
              invalid={Boolean(errors.hostUserId)}
              {...register('hostUserId')}
            >
              <option value="">
                {hosts.isPending ? 'Loading hosts…' : 'Choose a host…'}
              </option>
              {(hosts.data?.items ?? []).map((host) => (
                <option key={host.id} value={host.userId}>
                  {hostLabel(host)}
                </option>
              ))}
            </NativeSelect>
          )}
        </Field>

        <Field label="Purpose of visit" required error={errors.purpose?.message}>
          {({ id, describedBy }) => (
            <Textarea
              id={id} rows={3} aria-describedby={describedBy}
              invalid={Boolean(errors.purpose)}
              maxLength={LIMITS.purpose.max}
              placeholder="e.g. Meeting to discuss a research collaboration."
              {...register('purpose')}
            />
          )}
        </Field>

        <div className="grid gap-[var(--sp-4)] sm:grid-cols-2">
          <Field label="First day" required error={errors.visitFrom?.message}>
            {({ id, describedBy }) => (
              <Input
                id={id} type="date" aria-describedby={describedBy}
                invalid={Boolean(errors.visitFrom)}
                {...register('visitFrom')}
              />
            )}
          </Field>

          <Field label="Last day" required error={errors.visitTo?.message}>
            {({ id, describedBy }) => (
              <Input
                id={id} type="date" aria-describedby={describedBy}
                invalid={Boolean(errors.visitTo)}
                {...register('visitTo')}
              />
            )}
          </Field>
        </div>

        <div className="flex flex-wrap justify-end gap-[var(--sp-3)]">
          <Button type="button" variant="secondary" onClick={() => navigate('/visitor')}>
            Cancel
          </Button>
          <Button type="submit" loading={submit.isPending}>Submit request</Button>
        </div>
      </form>
    </div>
  );
}

/**
 * The best label the DTO allows. Reads "Associate Professor · Computer Science
 * · F-1042" — enough to narrow down, not enough to be certain, which is
 * exactly what the blocker above costs the visitor.
 */
function hostLabel(host: FacultyProfileResponse): string {
  return [host.designation, host.departmentName, host.employeeId]
    .filter(Boolean)
    .join(' · ') || `Faculty member #${host.userId}`;
}
