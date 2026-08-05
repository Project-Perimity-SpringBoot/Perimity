import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import { Button, Field, Input, NativeSelect, Textarea } from '@ui/index';
import { PageHeader } from '@components/data';
import { ErrorState, FormError } from '@components/feedback';
import { visitorRequestApi } from '@lib/api/services/gatepass.api';
import { campusApi } from '@lib/api/services/campus.api';
import { campusKeys, requestKeys } from '@lib/query/keys';
import { LIMITS } from '@lib/validation/patterns';
import { useApiFormErrors } from '@hooks/useApiForm';
import { useAuth } from '@hooks/useAuth';
import { visitorRequestSchema, type VisitorRequestValues } from '../schemas/visitor.schemas';

/**
 * Phase 5 screen 2 — apply for a visitor pass.
 *
 * Name, email, phone, purpose, campus, from, to. No semester — a visitor has
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
 * CAMPUS, NOT A HOST
 * ==========================================================================
 * The visitor picks the campus they are visiting; any faculty member of that
 * campus can approve it. The host picker this replaced could not show names -
 * FacultyProfileResponse carries only userId, departmentName, designation and
 * employeeId - so a visitor meeting Dr. Rao had to guess between three
 * Assistant Professors, and guessing wrong parked the request in an inbox
 * nobody was watching.
 *
 * hostUserId still exists on the DTO and is still accepted when a visitor
 * genuinely knows who invited them. It is no longer required for the request
 * to be actionable.
 */
export default function ApplyPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { identity } = useAuth();
  const [formErrors, setFormErrors] = useState<string[]>([]);

  /*
   * Active campuses only. The visitor is signed in by the time they reach this
   * screen, so the authenticated listing is available - blocker B12 only bites
   * on the registration page, which has no token yet.
   */
  const campuses = useQuery({
    queryKey: campusKeys.list(false),
    queryFn: () => campusApi.list(false),
  });

  const {
    register, handleSubmit, setError, formState: { errors },
  } = useForm<VisitorRequestValues>({
    resolver: zodResolver(visitorRequestSchema),
    defaultValues: {
      visitorName: '', visitorEmail: identity?.email ?? '', visitorPhone: '', purpose: '',
      campusId: undefined, visitFrom: '', visitTo: '',
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
        // Chosen by the visitor now. The server validates that it exists and
        // scopes the approval queue to it.
        campusId: values.campusId,
        visitFrom: values.visitFrom,
        visitTo: values.visitTo,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: requestKeys.all });
      navigate('/visitor/submitted');
    },
    onError: (error) => { setFormErrors([]); applyApiErrors(error); },
  });

  if (campuses.isError) {
    return <ErrorState error={campuses.error} onRetry={() => void campuses.refetch()} />;
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
          label="Which campus are you visiting"
          required
          hint="Any faculty member of that campus can approve your request."
          error={errors.campusId?.message}
        >
          {({ id, describedBy }) => (
            <NativeSelect
              id={id} aria-describedby={describedBy}
              disabled={campuses.isPending}
              invalid={Boolean(errors.campusId)}
              {...register('campusId')}
            >
              <option value="">
                {campuses.isPending ? 'Loading campuses…' : 'Choose a campus…'}
              </option>
              {(campuses.data ?? []).map((campus) => (
                <option key={campus.id} value={campus.id}>
                  {campus.name}
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
