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
import { ID_HINTS, ID_MAX_LENGTH, ID_NUMERIC_ONLY } from '@lib/validation/idDocuments';
import {
  GENDERS, GENDER_LABELS, ID_TYPES, ID_TYPE_LABELS,
  PURPOSE_TYPES, PURPOSE_TYPE_LABELS, VISITOR_TYPES, VISITOR_TYPE_LABELS,
} from '@/types/enums';
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
    register, handleSubmit, setError, watch, formState: { errors },
  } = useForm<VisitorRequestValues>({
    resolver: zodResolver(visitorRequestSchema),
    /*
     * Validate once a field has been touched, then live as it is corrected.
     * The default is submit-only, which is why an error could sit under a field
     * the visitor had already fixed - it had no reason to re-check until the
     * next submit.
     */
    mode: 'onTouched',
    reValidateMode: 'onChange',
    defaultValues: {
      visitorName: identity?.name ?? '', visitorEmail: identity?.email ?? '', visitorPhone: '', purpose: '',
      purposeType: undefined, visitorType: undefined, gender: '', dateOfBirth: '',
      idType: '', idNumber: '',
      campusId: undefined, visitFrom: '', visitTo: '',
    },
  });
  const applyApiErrors = useApiFormErrors<VisitorRequestValues>(setError, setFormErrors);

  // Drives the ID hint. Watched rather than read on submit so it updates as
  // soon as the visitor picks a document.
  const selectedIdType = watch('idType');

  const submit = useMutation({
    mutationFn: (values: VisitorRequestValues) =>
      visitorRequestApi.submit({
        visitorName: values.visitorName,
        visitorEmail: values.visitorEmail,
        // Empty string would store a blank phone rather than none.
        visitorPhone: values.visitorPhone ? values.visitorPhone : null,
        purpose: values.purpose || null,
        // Chosen by the visitor now. The server validates that it exists and
        // scopes the approval queue to it.
        campusId: values.campusId,
        purposeType: values.purposeType,
        visitorType: values.visitorType,
        // '' is "not answered". Sending it would store a blank rather than
        // nothing, and an empty string is not a valid enum server-side.
        gender: values.gender || null,
        dateOfBirth: values.dateOfBirth || null,
        idType: values.idType || null,
        idNumber: values.idNumber || null,
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
        className="flex flex-col gap-[var(--sp-5)]"
      >
        <FormError messages={formErrors} />

<section className="surface-card flex flex-col gap-[var(--sp-5)] p-[var(--sp-6)]">
          <div className="flex items-center gap-[var(--sp-3)]">
            <span
              aria-hidden
              className="flex size-7 shrink-0 items-center justify-center rounded-full bg-[var(--brand-100)] text-caption font-semibold text-[var(--brand-700)]"
            >
              1
            </span>
            <h2 className="text-body-lg font-medium text-[var(--ink-900)]">
              Visitor information
            </h2>
          </div>

          <div className="grid gap-[var(--sp-4)] md:grid-cols-3">
            <Field
              label="Full name"
              required
              hint="From your account. Correct it here if it should read differently on your pass."
              error={errors.visitorName?.message}
            >
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
              hint="Optional. 10 digits, no country code."
              error={errors.visitorPhone?.message}
            >
              {({ id, describedBy }) => (
                <Input
                  id={id} type="tel" aria-describedby={describedBy} autoComplete="tel"
                  invalid={Boolean(errors.visitorPhone)}
                  placeholder="9876543210"
                  maxLength={10}
                  inputMode="numeric"
                  {...register('visitorPhone')}
                />
              )}
            </Field>
          </div>
        </section>

        <section
          aria-labelledby="section-2"
          className="surface-card flex flex-col gap-[var(--sp-5)] p-[var(--sp-6)]"
        >
          {/*
            * A section with aria-labelledby, not a fieldset. A <legend>
            * renders inside the border and ignores the padding, so the
            * heading sat on the border and the fields pressed against the
            * edges. This announces the group name just as well and lays
            * out predictably.
            */}
          <div className="flex items-center gap-[var(--sp-3)]">
            <span
              aria-hidden
              className="flex size-7 shrink-0 items-center justify-center rounded-full
                         bg-[var(--brand-100)] text-caption font-semibold
                         text-[var(--brand-700)]"
            >
              2
            </span>
            <h2 id="section-2" className="text-body-lg font-medium text-[var(--ink-900)]">
              Visit details
            </h2>
          </div>

          <div className="grid gap-[var(--sp-4)] sm:grid-cols-2">
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

            <Field
              label="Type of visitor"
              required
              error={errors.visitorType?.message}
            >
              {({ id, describedBy }) => (
                <NativeSelect
                  id={id} aria-describedby={describedBy}
                  invalid={Boolean(errors.visitorType)}
                  {...register('visitorType')}
                >
                  <option value="">Choose…</option>
                  {VISITOR_TYPES.map((v) => (
                    <option key={v} value={v}>{VISITOR_TYPE_LABELS[v]}</option>
                  ))}
                </NativeSelect>
              )}
            </Field>
          </div>

          <Field
            label="Purpose of visit"
            required
            error={errors.purposeType?.message}
          >
            {({ id, describedBy }) => (
              <NativeSelect
                id={id} aria-describedby={describedBy}
                invalid={Boolean(errors.purposeType)}
                {...register('purposeType')}
              >
                <option value="">Choose…</option>
                {PURPOSE_TYPES.map((v) => (
                  <option key={v} value={v}>{PURPOSE_TYPE_LABELS[v]}</option>
                ))}
              </NativeSelect>
            )}
          </Field>

          <div className="grid gap-[var(--sp-4)] sm:grid-cols-2">
            <Field
              label="First day"
              required
              hint="Visiting for one day? Put the same date in both."
              error={errors.visitFrom?.message}
            >
              {({ id, describedBy }) => (
                <Input
                  id={id} type="date" aria-describedby={describedBy}
                  invalid={Boolean(errors.visitFrom)}
                  // Mirrors the server's @FutureOrPresent. Without it the picker
                  // happily offers yesterday and the rejection arrives later.
                  min={new Date().toISOString().slice(0, 10)}
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
        </section>

        <section
          aria-labelledby="section-3"
          className="surface-card flex flex-col gap-[var(--sp-5)] p-[var(--sp-6)]"
        >
          {/*
            * A section with aria-labelledby, not a fieldset. A <legend>
            * renders inside the border and ignores the padding, so the
            * heading sat on the border and the fields pressed against the
            * edges. This announces the group name just as well and lays
            * out predictably.
            */}
          <div className="flex items-center gap-[var(--sp-3)]">
            <span
              aria-hidden
              className="flex size-7 shrink-0 items-center justify-center rounded-full
                         bg-[var(--brand-100)] text-caption font-semibold
                         text-[var(--brand-700)]"
            >
              3
            </span>
            <h2 id="section-3" className="text-body-lg font-medium text-[var(--ink-900)]">
              Identification and notes
            </h2>
          </div>

          <div className="grid gap-[var(--sp-4)] md:grid-cols-3">
            <Field
              label="Date of birth"
              hint="Optional. Used to confirm your identity at the gate."
              error={errors.dateOfBirth?.message}
            >
              {({ id, describedBy }) => (
                <Input
                  id={id} type="date" aria-describedby={describedBy}
                  invalid={Boolean(errors.dateOfBirth)}
                  max={new Date().toISOString().slice(0, 10)}
                  {...register('dateOfBirth')}
                />
              )}
            </Field>

            <Field
              label="ID type"
              hint="Optional, but bring the document you name here."
              error={errors.idType?.message}
            >
              {({ id, describedBy }) => (
                <NativeSelect
                  id={id} aria-describedby={describedBy}
                  invalid={Boolean(errors.idType)}
                  {...register('idType')}
                >
                  <option value="">None</option>
                  {ID_TYPES.map((v) => (
                    <option key={v} value={v}>{ID_TYPE_LABELS[v]}</option>
                  ))}
                </NativeSelect>
              )}
            </Field>


            {/* The hint follows the chosen document - a generic one would be wrong
                for three of the four. */}
            <Field
              label="ID number"
              /* maxLength follows the chosen document, so a 13th digit cannot be
                 typed at all. The old catch-all of 40 let you overrun every
                 format and only complained afterwards. */
              hint={selectedIdType ? ID_HINTS[selectedIdType] : 'Choose an ID type first.'}
              error={errors.idNumber?.message}
            >
              {({ id, describedBy }) => (
                <Input
                  id={id} aria-describedby={describedBy}
                  invalid={Boolean(errors.idNumber)}
                  maxLength={selectedIdType ? ID_MAX_LENGTH[selectedIdType] : 20}
                  inputMode={selectedIdType && ID_NUMERIC_ONLY[selectedIdType] ? 'numeric' : 'text'}
                  disabled={!selectedIdType}
                  className={selectedIdType && !ID_NUMERIC_ONLY[selectedIdType] ? 'uppercase' : undefined}
                  autoComplete="off"
                  {...register('idNumber')}
                />
              )}
            </Field>
          </div>

          <div className="grid gap-[var(--sp-4)] sm:grid-cols-2">
            <Field
              label="Gender"
              hint="Optional."
              error={errors.gender?.message}
            >
              {({ id, describedBy }) => (
                <NativeSelect
                  id={id} aria-describedby={describedBy}
                  invalid={Boolean(errors.gender)}
                  {...register('gender')}
                >
                  <option value="">Prefer not to answer</option>
                  {GENDERS.map((v) => (
                    <option key={v} value={v}>{GENDER_LABELS[v]}</option>
                  ))}
                </NativeSelect>
              )}
            </Field>
          </div>

          <Field label="Anything else your host should know" error={errors.purpose?.message}>
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
        </section>


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
