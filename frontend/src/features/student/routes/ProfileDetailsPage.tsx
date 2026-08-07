import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Link } from 'react-router';
import { AlertTriangle, ArrowLeft, Image as ImageIcon, Info, Trash2 } from 'lucide-react';
import { Button, Field, Input, NativeSelect, SkeletonText, Textarea } from '@ui/index';
import { ConfirmDialog, ErrorState, FormError } from '@components/feedback';
import { NotFoundError } from '@lib/api/errors';
import { PageHeader } from '@components/data';
import { AuthedImage, FileDropzone } from '@components/upload';
import { ProfileVerificationBadge } from '@components/pass/StatusBadge';
import { studentApi } from '@lib/api/services/user.api';
import { passKeys, profileKeys } from '@lib/query/keys';
import { UPLOAD_RULES } from '@lib/validation/patterns';
import { useApiFormErrors } from '@hooks/useApiForm';
import { useToast } from '@hooks/useToast';
import { formatDateTime } from '@lib/format/datetime';
import { GENDERS, GENDER_LABELS } from '@/types/enums';
import {
  studentSelfDetailsSchema, type StudentSelfDetailsValues,
} from '../schemas/student.schemas';

/**
 * The student's own details, their photo, and the request to have them checked.
 *
 * ==========================================================================
 * THE ONE EDIT SCREEN. ProfileEditPage MERGED INTO THIS ONE.
 * ==========================================================================
 * There were two. This one replaced the whole record and submitted it for
 * review; ProfileEditPage patched the photo. The argument for keeping them
 * apart was that two save contracts with two different consequences should not
 * share a page.
 *
 * It did not survive contact with the product. BOTH screens uploaded a photo,
 * so the one field with a real consequence was editable in two places and each
 * paused the pass - while ProfileEditPage's own comment claimed to be the only
 * screen that could. The profile page then offered "Edit" and "Edit these"
 * pointing at different screens, and a student who picked the wrong one got a
 * warning that did not describe what they were about to do.
 *
 * One screen, one photo control, one Save, one Submit. /student/profile/edit
 * redirects here; its Remove photo action came with it.
 *
 * ==========================================================================
 * SAVE AND SUBMIT ARE SEPARATE BUTTONS
 * ==========================================================================
 * A student part-way through the form must be able to keep their work without
 * it landing in a reviewer's queue half-finished. Save writes; Submit hands it
 * over and locks it.
 *
 * ==========================================================================
 * THE CONFIRMATION ON A VERIFIED PROFILE
 * ==========================================================================
 * Editing verified details is allowed, and it clears the verification. That is
 * correct — a verified record must never describe details nobody checked — but
 * it is not what a student expects from pressing Save, so it is confirmed
 * first. The server does this regardless of what this screen shows; the dialog
 * exists so the outcome is not a surprise.
 *
 * The state rules mirrored here (editable, canSubmit) are UI hints. The server
 * enforces all of them and this screen would still be safe without them.
 */
/**
 * "" and "   " both mean "no value", so they go to the server as null rather
 * than as a blank string. Mirrors trimToNull in StudentProfileService.
 *
 * Written as a function rather than inline ternaries because
 * `v?.trim() ? v : null` does not narrow — TypeScript still sees
 * `string | undefined` on the truthy branch, since the test and the value are
 * different expressions.
 */
const blankToNull = (value: string | undefined): string | null => {
  const trimmed = value?.trim();
  return trimmed ? trimmed : null;
};

export default function ProfileDetailsPage() {
  const toast = useToast();
  const queryClient = useQueryClient();

  const [formErrors, setFormErrors] = useState<string[]>([]);
  const [pendingValues, setPendingValues] = useState<StudentSelfDetailsValues | null>(null);
  const [confirmSubmit, setConfirmSubmit] = useState(false);
  const [photoFile, setPhotoFile] = useState<File | null>(null);
  const [removingPhoto, setRemovingPhoto] = useState(false);

  /*
   * A 404 here is NOT an error state on this screen.
   *
   * An account in auth-service and a profile in user-service are separate
   * records, and an account can exist without one — every student created by
   * anything other than the faculty Add Student screen is in that position.
   * Those students used to open this page and get "Not found. No student
   * profile exists for account 21", which is true, useless, and offers them
   * nothing to do about it.
   *
   * A student with no profile is a student who has not filled anything in yet,
   * which is exactly what a blank form is for. Saving it creates the row —
   * PUT /students/me/details is create-or-replace server-side.
   *
   * retry: false because retrying a 404 three times only slows down the empty
   * state it is going to show anyway.
   */
  const profile = useQuery({
    queryKey: profileKeys.myStudent(),
    queryFn: () => studentApi.me(),
    retry: false,
  });

  const missingProfile = profile.error instanceof NotFoundError;
  /** False until the student's first save creates the row. */
  const hasProfile = Boolean(profile.data?.id);

  const status = profile.data?.verificationStatus ?? 'DRAFT';
  const locked = status === 'SUBMITTED';
  const wasVerified = status === 'VERIFIED';
  const hasPhoto = Boolean(profile.data?.photoS3Key);

  /*
   * The stored value is a key, not a URL, so it needs signing before it can go
   * in an <img>. The link is short-lived and minted per read — never stored,
   * which is the point of the whole mechanism.
   */
  const photoUrl = useQuery({
    queryKey: profileKeys.photoUrl('student', profile.data?.id ?? 0),
    queryFn: () => studentApi.photoUrl(profile.data?.id as number),
    enabled: Boolean(profile.data?.id) && hasPhoto,
  });

  const uploadPhoto = useMutation({
    mutationFn: (file: File) => studentApi.uploadPhoto(profile.data?.id as number, file),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: profileKeys.myStudent() });
      void queryClient.invalidateQueries({
        queryKey: profileKeys.photoUrl('student', profile.data?.id ?? 0),
      });
      // Passes may have moved to PAUSED server-side, and a verified profile has
      // just been reset to DRAFT. Both need the cached copies dropped.
      void queryClient.invalidateQueries({ queryKey: passKeys.all });
      setPhotoFile(null);
      toast.success('Photo uploaded');
    },
    onError: (error) => {
      setPhotoFile(null);
      toast.fromError(error, 'That photo could not be uploaded.');
    },
  });

  /*
   * Carried over from /student/profile/edit when that screen merged into this
   * one. Same invalidations as the upload above and for the same reasons: the
   * profile row changed, the signed URL now points at nothing, and the pass may
   * have moved to PAUSED server-side.
   */
  const removePhoto = useMutation({
    mutationFn: () => studentApi.removePhoto(profile.data?.id as number),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: profileKeys.myStudent() });
      void queryClient.invalidateQueries({
        queryKey: profileKeys.photoUrl('student', profile.data?.id ?? 0),
      });
      void queryClient.invalidateQueries({ queryKey: passKeys.all });
      setRemovingPhoto(false);
      toast.success('Photo removed');
    },
    onError: (error) => toast.fromError(error, 'That photo could not be removed.'),
  });

  const form = useForm<StudentSelfDetailsValues>({
    resolver: zodResolver(studentSelfDetailsSchema),
    /*
     * `values` rather than `defaultValues`: the profile arrives after the first
     * render, and defaultValues would leave every box empty behind it.
     *
     * The country codes fall back to +91 only when the profile has none at all
     * — a student who has deliberately set another country keeps it.
     */
    values: {
      firstName: profile.data?.firstName ?? '',
      middleName: profile.data?.middleName ?? '',
      lastName: profile.data?.lastName ?? '',
      dateOfBirth: profile.data?.dateOfBirth ?? '',
      gender: profile.data?.gender ?? 'PREFER_NOT_TO_SAY',
      address: profile.data?.address ?? '',
      phoneCountryCode: profile.data?.phoneCountryCode ?? '+91',
      phoneNumber: profile.data?.phoneNumber ?? '',
      altPhoneCountryCode: profile.data?.altPhoneCountryCode ?? '',
      altPhoneNumber: profile.data?.altPhoneNumber ?? '',
    },
  });
  const applyApiErrors = useApiFormErrors<StudentSelfDetailsValues>(form.setError, setFormErrors);

  const save = useMutation({
    mutationFn: (values: StudentSelfDetailsValues) =>
      studentApi.updateOwnDetails({
        firstName: values.firstName,
        middleName: blankToNull(values.middleName),
        lastName: values.lastName,
        dateOfBirth: values.dateOfBirth,
        gender: values.gender,
        address: values.address,
        phoneCountryCode: values.phoneCountryCode,
        phoneNumber: values.phoneNumber,
        altPhoneCountryCode: blankToNull(values.altPhoneCountryCode),
        altPhoneNumber: blankToNull(values.altPhoneNumber),
      }),
    onSuccess: (updated) => {
      void queryClient.invalidateQueries({ queryKey: profileKeys.myStudent() });
      setPendingValues(null);
      toast.success(
        wasVerified
          ? 'Saved. Your details are no longer verified — submit them again when you are ready.'
          : 'Details saved',
      );
      /*
       * reset() with the saved values clears isDirty, which is what enables the
       * Submit button. Without it a student who has just saved still sees "Save
       * your changes first" and no way to proceed.
       *
       * Deliberately no navigate() — saving and then submitting is the normal
       * path, and bouncing to another page would make that two journeys.
       */
      form.reset({
        firstName: updated.firstName ?? '',
        middleName: updated.middleName ?? '',
        lastName: updated.lastName ?? '',
        dateOfBirth: updated.dateOfBirth ?? '',
        gender: updated.gender ?? 'PREFER_NOT_TO_SAY',
        address: updated.address ?? '',
        phoneCountryCode: updated.phoneCountryCode ?? '+91',
        phoneNumber: updated.phoneNumber ?? '',
        altPhoneCountryCode: updated.altPhoneCountryCode ?? '',
        altPhoneNumber: updated.altPhoneNumber ?? '',
      });
    },
    onError: (error) => { setFormErrors([]); applyApiErrors(error); setPendingValues(null); },
  });

  const submit = useMutation({
    mutationFn: () => studentApi.submitOwnDetails(),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: profileKeys.myStudent() });
      setConfirmSubmit(false);
      toast.success('Sent to faculty. You will see the outcome here.');
    },
    onError: (error) => { setConfirmSubmit(false); setFormErrors([]); applyApiErrors(error); },
  });

  const onSubmitForm = (values: StudentSelfDetailsValues) => {
    if (wasVerified) { setPendingValues(values); return; }
    save.mutate(values);
  };

  if (profile.isPending) return <SkeletonText lines={8} />;
  // Only a REAL failure stops the page. A missing profile renders a blank form.
  if (profile.isError && !missingProfile) {
    return <ErrorState error={profile.error} onRetry={() => void profile.refetch()} />;
  }

  const unsaved = form.formState.isDirty;

  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      <PageHeader
        title="My details"
        description="Your own information, checked by faculty before it counts."
        actions={
          <Link to="/student/profile">
            <Button variant="ghost"><ArrowLeft className="size-4" /> Back to profile</Button>
          </Link>
        }
      />

      {/* ---------------------------------------------------------------
          Status. First thing on the page because it decides what the rest
          of the page can do.
         --------------------------------------------------------------- */}
      <div className="surface-card space-y-[var(--sp-2)] p-[var(--sp-6)]">
        <div className="flex items-center gap-[var(--sp-2)]">
          <ProfileVerificationBadge status={status} />
          {profile.data?.submittedAt && status === 'SUBMITTED' && (
            <span className="text-caption text-[var(--ink-500)]">
              sent {formatDateTime(profile.data?.submittedAt)}
            </span>
          )}
          {profile.data?.verifiedAt && status === 'VERIFIED' && (
            <span className="text-caption text-[var(--ink-500)]">
              checked {formatDateTime(profile.data?.verifiedAt)}
            </span>
          )}
        </div>

        {status === 'REJECTED' && profile.data?.verificationRemarks && (
          <p className="text-body flex gap-[var(--sp-2)]">
            <AlertTriangle className="size-4 shrink-0 mt-[2px]" aria-hidden />
            {/*
              The reviewer's words, rendered as text. Never as HTML — this is
              free text written by one user and read by another.
            */}
            <span><strong>Faculty asked for changes:</strong> {profile.data?.verificationRemarks}</span>
          </p>
        )}

        {locked && (
          <p className="text-body flex gap-[var(--sp-2)] text-[var(--ink-500)]">
            <Info className="size-4 shrink-0 mt-[2px]" aria-hidden />
            Faculty are checking these now, so they cannot be edited. You will be
            able to change them again once there is an outcome.
          </p>
        )}

        {wasVerified && (
          <p className="text-body flex gap-[var(--sp-2)] text-[var(--ink-500)]">
            <Info className="size-4 shrink-0 mt-[2px]" aria-hidden />
            Editing verified details means they have to be checked again.
          </p>
        )}
      </div>

      <FormError messages={formErrors} />

      {/* ---------------------------------------------------------------
          Passport photo. Required before submitting, and first on the page
          because it is the only field here that does the access-control job
          — it is what a guard holds against the face at the gate. The rest
          is information a reviewer reads.

          Uploading is immediate and separate from Save: it is a file going
          to storage, not a form value, and pretending otherwise would mean
          holding the file in memory until an unrelated button is pressed.
         --------------------------------------------------------------- */}
      <section className="surface-card space-y-[var(--sp-4)] p-[var(--sp-6)]">
        <div className="flex items-start justify-between gap-[var(--sp-4)]">
          <div className="min-w-0">
            <h2 className="text-label text-[var(--ink-900)]">Passport photo</h2>
            <p className="text-caption text-[var(--ink-500)]">
              Required. A guard checks this against your face at the gate, so use a
              clear, front-facing picture.
            </p>
          </div>
          {/*
            The page-level badge above already reports this status, and it is a
            PROFILE status, not a photo one. Repeating it beside the photo read
            as "the photo has not been submitted", which is a different claim
            and not one this component can make.
          */}
        </div>

        <div className="flex flex-wrap items-start gap-[var(--sp-6)]">
          <div className="shrink-0">
            {/*
              AuthedImage, not a plain <img>. In local-storage mode the URL is
              behind the JWT filter and a browser sends no Authorization header
              with an image request, so a bare img renders as broken.

              Not Avatar either: Avatar falls back to initials, which would show
              a tidy coloured circle to a student whose photo failed to load and
              leave them believing it worked.
            */}
            <AuthedImage
              url={photoUrl.data?.url}
              alt="Your passport photo"
              className="size-32 rounded-[var(--r-md)] border border-[var(--border)]"
              fallback={
                <div className="flex size-32 flex-col items-center justify-center gap-[var(--sp-2)] rounded-[var(--r-md)] border border-dashed border-[var(--border-strong)] text-[var(--ink-500)]">
                  <ImageIcon className="size-6" aria-hidden />
                  <span className="text-caption">
                    {hasPhoto ? 'Could not load' : 'No photo'}
                  </span>
                </div>
              }
            />
          </div>

          {!locked && (
            <div className="min-w-[16rem] flex-1 space-y-[var(--sp-3)]">
              {/*
                The upload endpoint is keyed by profile id, so there is nothing
                to upload against until the row exists. Rather than let the
                student pick a file and fail, say what to do first — filling in
                the form and saving creates the profile.
              */}
              {hasProfile ? (
                <FileDropzone
                  rule={UPLOAD_RULES.photo}
                  file={photoFile}
                  onSelect={(file) => { setPhotoFile(file); uploadPhoto.mutate(file); }}
                  onClear={() => setPhotoFile(null)}
                  disabled={uploadPhoto.isPending}
                />
              ) : (
                <p className="text-body rounded-[var(--r-md)] bg-[var(--surface-sunken)] px-[var(--sp-3)] py-[var(--sp-3)] text-[var(--ink-500)]">
                  Fill in your details below and press <strong>Save</strong> first.
                  You can add your photo straight after.
                </p>
              )}
              {uploadPhoto.isPending && (
                <p className="text-caption text-[var(--ink-500)]">Uploading…</p>
              )}
              {hasPhoto && status === 'VERIFIED' && (
                <p className="text-caption text-[var(--ink-500)]">
                  Replacing your photo means your details have to be checked again,
                  and pauses any pass you hold.
                </p>
              )}
              {/*
                CARRIED OVER from /student/profile/edit, which this screen
                replaced. That page could remove a photo and this one could
                only replace it, so merging without this would have quietly
                taken the ability away - the one thing a merge must not do.

                Behind a confirm, because a profile with no photo cannot be
                submitted for verification at all: the server names "passport
                photo" among the fields that must be filled in.
              */}
              {hasPhoto && (
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  onClick={() => setRemovingPhoto(true)}
                  disabled={uploadPhoto.isPending}
                >
                  <Trash2 aria-hidden />Remove current photo
                </Button>
              )}
            </div>
          )}
        </div>
      </section>

      <form
        onSubmit={(e) => { void form.handleSubmit(onSubmitForm)(e); }}
        className="space-y-[var(--sp-4)]"
        noValidate
      >
        <fieldset disabled={locked} className="space-y-[var(--sp-4)] disabled:opacity-60">
          <div className="grid gap-[var(--sp-4)] sm:grid-cols-3">
            <Field label="First name" required error={form.formState.errors.firstName?.message}>
              {({ id, describedBy }) => (
                <Input id={id} aria-describedby={describedBy} autoComplete="given-name"
                       invalid={Boolean(form.formState.errors.firstName)}
                       {...form.register('firstName')} />
              )}
            </Field>
            <Field label="Middle name" error={form.formState.errors.middleName?.message}>
              {({ id, describedBy }) => (
                <Input id={id} aria-describedby={describedBy} autoComplete="additional-name"
                       invalid={Boolean(form.formState.errors.middleName)}
                       {...form.register('middleName')} />
              )}
            </Field>
            <Field label="Last name" required error={form.formState.errors.lastName?.message}>
              {({ id, describedBy }) => (
                <Input id={id} aria-describedby={describedBy} autoComplete="family-name"
                       invalid={Boolean(form.formState.errors.lastName)}
                       {...form.register('lastName')} />
              )}
            </Field>
          </div>

          <p className="text-caption text-[var(--ink-500)]">
            Your pass carries the name on your account. These are for your record.
          </p>

          {/*
            The government ID, read-only, because faculty verify it and the
            student could not see it.

            It is entered by whoever enrolled the student and is not part of
            this form, so a mistyped digit was invisible on the one screen that
            asks "are these details correct?" - the student was being asked to
            attest to a value the page never showed them.

            Read-only rather than editable: changing it PAUSES every pass the
            student holds (StudentProfileService adds "government ID" to
            sensitiveChanges), and a field that can suspend your own gate access
            does not belong on a form whose save button is pressed casually.
            The edit screen keeps that, with its warning.

            Masked, never full - the server sends govIdMasked and govIdPresent
            and never the real digits.
          */}
          {profile.data?.govIdPresent ? (
            <div className="flex flex-wrap items-baseline gap-[var(--sp-2)]">
              <span className="text-label text-[var(--ink-900)]">Government ID</span>
              <span className="text-mono text-[var(--ink-700)]">
                {profile.data.govIdMasked ?? '••••••••'}
              </span>
              <span className="text-caption text-[var(--ink-500)]">
                on file — if this is wrong, ask the faculty who enrolled you to correct it
                before you submit.
              </span>
            </div>
          ) : null}

          <div className="grid gap-[var(--sp-4)] sm:grid-cols-2">
            <Field label="Date of birth" required error={form.formState.errors.dateOfBirth?.message}>
              {({ id, describedBy }) => (
                <Input id={id} type="date" aria-describedby={describedBy} autoComplete="bday"
                       invalid={Boolean(form.formState.errors.dateOfBirth)}
                       {...form.register('dateOfBirth')} />
              )}
            </Field>
            <Field label="Gender" required error={form.formState.errors.gender?.message}>
              {({ id, describedBy }) => (
                <NativeSelect id={id} aria-describedby={describedBy}
                              {...form.register('gender')}>
                  {GENDERS.map((g) => (
                    <option key={g} value={g}>{GENDER_LABELS[g]}</option>
                  ))}
                </NativeSelect>
              )}
            </Field>
          </div>

          <Field label="Address" required error={form.formState.errors.address?.message}>
            {({ id, describedBy }) => (
              <Textarea id={id} rows={3} aria-describedby={describedBy}
                        autoComplete="street-address"
                        invalid={Boolean(form.formState.errors.address)}
                        {...form.register('address')} />
            )}
          </Field>

          {/*
            Country code and number are separate inputs because they are
            separate values on the server. A single box would need this screen
            to guess where the code ends, and the guess is wrong for every
            country with a 1, 3 or 4 digit code.

            The country-code error is folded into this Field's error slot rather
            than getting its own, so one phone number never shows two messages
            stacked under it.
          */}
          <div className="grid gap-[var(--sp-4)] sm:grid-cols-2">
            <Field
              label="Phone"
              required
              error={
                form.formState.errors.phoneCountryCode?.message
                ?? form.formState.errors.phoneNumber?.message
              }
            >
              {({ id, describedBy }) => (
                <div className="flex gap-[var(--sp-2)]">
                  <Input className="w-[5.5rem]" aria-label="Country code"
                         aria-describedby={describedBy}
                         invalid={Boolean(form.formState.errors.phoneCountryCode)}
                         {...form.register('phoneCountryCode')} />
                  <Input id={id} className="flex-1" inputMode="numeric"
                         maxLength={10} placeholder="9876543210"
                         autoComplete="tel-national" aria-label="Phone number"
                         aria-describedby={describedBy}
                         invalid={Boolean(form.formState.errors.phoneNumber)}
                         {...form.register('phoneNumber', {
                           onChange: (e) => {
                             const digits = e.target.value.replace(/\D/g, '').slice(0, 10);
                             form.setValue('phoneNumber', digits, { shouldValidate: true });
                           },
                         })} />
                </div>
              )}
            </Field>

            <Field
              label="Another phone"
              hint="Optional."
              error={
                form.formState.errors.altPhoneCountryCode?.message
                ?? form.formState.errors.altPhoneNumber?.message
              }
            >
              {({ id, describedBy }) => (
                <div className="flex gap-[var(--sp-2)]">
                  <Input className="w-[5.5rem]" aria-label="Second country code"
                         placeholder="+91" aria-describedby={describedBy}
                         invalid={Boolean(form.formState.errors.altPhoneCountryCode)}
                         {...form.register('altPhoneCountryCode')} />
                  <Input id={id} className="flex-1" inputMode="numeric"
                         maxLength={10} placeholder="9876543210"
                         aria-label="Second phone number" aria-describedby={describedBy}
                         invalid={Boolean(form.formState.errors.altPhoneNumber)}
                         {...form.register('altPhoneNumber', {
                           onChange: (e) => {
                             const digits = e.target.value.replace(/\D/g, '').slice(0, 10);
                             form.setValue('altPhoneNumber', digits, { shouldValidate: true });
                           },
                         })} />
                </div>
              )}
            </Field>
          </div>
        </fieldset>

        <div className="flex flex-wrap items-center gap-[var(--sp-3)]">
          <Button type="submit" disabled={locked || save.isPending}>
            {save.isPending ? 'Saving…' : 'Save'}
          </Button>

          {/*
            Submitting with unsaved edits would send the reviewer the previously
            saved values while the student is looking at newer ones on screen.
            Disabled rather than auto-saving, so nothing is written that the
            student did not ask to write.

            The photo requirement is enforced by the server too — this only
            turns a 409 after the click into a reason before it.
          */}
          <Button
            type="button"
            variant="secondary"
            disabled={locked || unsaved || !hasPhoto || save.isPending || submit.isPending}
            onClick={() => setConfirmSubmit(true)}
          >
            Submit for verification
          </Button>

          {/*
            One reason at a time, most-blocking first. Two hints side by side
            read as a list of complaints rather than a next step.
          */}
          {!locked && !hasPhoto && (
            <span className="text-caption text-[var(--ink-500)]">
              Upload your passport photo first.
            </span>
          )}
          {!locked && hasPhoto && unsaved && (
            <span className="text-caption text-[var(--ink-500)]">Save your changes first.</span>
          )}
        </div>
      </form>

      <ConfirmDialog
        open={pendingValues !== null}
        onOpenChange={(open) => { if (!open) setPendingValues(null); }}
        title="This clears your verification"
        description={
          'These details have been checked by faculty. Saving changes means they '
          + 'go back to not verified, and someone has to check them again.'
        }
        confirmLabel="Save and clear verification"
        loading={save.isPending}
        onConfirm={() => { if (pendingValues) save.mutate(pendingValues); }}
      />

      <ConfirmDialog
        open={confirmSubmit}
        onOpenChange={(open) => { if (!open) setConfirmSubmit(false); }}
        title="Send these to faculty?"
        description="You will not be able to edit them while they are being checked."
        confirmLabel="Send"
        loading={submit.isPending}
        onConfirm={() => submit.mutate()}
      />

      <ConfirmDialog
        open={removingPhoto}
        onOpenChange={(open) => { if (!open) setRemovingPhoto(false); }}
        title="Remove your photo?"
        description="Without a photo a guard has nothing to check your face against, you cannot submit your details for checking, and any pass you hold will pause."
        confirmLabel="Remove"
        destructive
        loading={removePhoto.isPending}
        onConfirm={() => removePhoto.mutate()}
      />
    </div>
  );
}
