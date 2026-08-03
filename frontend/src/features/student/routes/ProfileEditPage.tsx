import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Link, useNavigate } from 'react-router';
import { AlertTriangle, ArrowLeft, Trash2 } from 'lucide-react';
import {
  Button, Field, Input, NativeSelect, SkeletonText, Textarea,
} from '@ui/index';
import { ConfirmDialog, ErrorState, FormError } from '@components/feedback';
import { PageHeader } from '@components/data';
import { FileDropzone } from '@components/upload';
import { departmentApi, studentApi } from '@lib/api/services/user.api';
import { departmentKeys, passKeys, profileKeys } from '@lib/query/keys';
import { UPLOAD_RULES } from '@lib/validation/patterns';
import { useApiFormErrors } from '@hooks/useApiForm';
import { useAuth } from '@hooks/useAuth';
import { useToast } from '@hooks/useToast';
import { studentProfileSchema, type StudentProfileValues } from '../schemas/student.schemas';

/**
 * Phase 3 screen 7 — profile edit.
 *
 * ==========================================================================
 * THREE WARNINGS, ALL REQUIRED, AND WHY THAT IS NOT OVERKILL
 * ==========================================================================
 * Four fields pause every active pass when changed: name, photo, government ID
 * and department. Each gets
 *
 *   1. a warning glyph on the field itself   - visible while deciding
 *   2. a persistent banner above the form    - visible before starting
 *   3. a confirmation modal naming the field - unavoidable before saving
 *
 * Three feels like a lot until you consider the failure: a student edits their
 * department on Sunday, notices nothing, and is refused at the gate on Monday
 * morning in front of a queue. The pause is correct behaviour — the whole point
 * is that a guard's visual check stays meaningful — but being surprised by it
 * is not.
 *
 * The modal names the specific field that triggered it rather than saying "some
 * of your changes", because a student who edited four things needs to know
 * which one costs them their pass.
 *
 * NAME IS NOT EDITABLE HERE. It lives on the auth-service user record, not the
 * student profile, and this screen only has PUT /students/{id}. Rendering a
 * name field that silently fails to save would be worse than saying so.
 */
export default function ProfileEditPage() {
  const { identity } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [formErrors, setFormErrors] = useState<string[]>([]);
  const [pendingValues, setPendingValues] = useState<StudentProfileValues | null>(null);
  const [photoFile, setPhotoFile] = useState<File | null>(null);
  const [confirmingPhoto, setConfirmingPhoto] = useState(false);
  const [removingPhoto, setRemovingPhoto] = useState(false);

  const profile = useQuery({
    queryKey: profileKeys.myStudent(),
    queryFn: () => studentApi.me(),
  });

  const departments = useQuery({
    queryKey: departmentKeys.list(identity?.campusId ?? undefined, true),
    queryFn: () => departmentApi.list(identity?.campusId ?? undefined, true),
    enabled: identity?.campusId != null,
  });

  const form = useForm<StudentProfileValues>({
    resolver: zodResolver(studentProfileSchema),
    values: {
      departmentId: profile.data?.departmentId ?? '',
      rollNo: profile.data?.rollNo ?? '',
      govId: '',
      address: profile.data?.address ?? '',
    },
  });
  const applyApiErrors = useApiFormErrors<StudentProfileValues>(form.setError, setFormErrors);

  const invalidateAfterPause = () => {
    void queryClient.invalidateQueries({ queryKey: profileKeys.myStudent() });
    // Passes may have moved to PAUSED server-side. Without this the banner does
    // not appear until a reload, which is the one moment it needs to.
    void queryClient.invalidateQueries({ queryKey: passKeys.all });
  };

  const save = useMutation({
    mutationFn: (values: StudentProfileValues) =>
      studentApi.update(profile.data?.id as number, {
        departmentId: values.departmentId === '' ? null : Number(values.departmentId),
        ...(values.rollNo ? { rollNo: values.rollNo } : { rollNo: null }),
        // An empty govId means "leave it alone" - the server never sent us the
        // real one, so sending "" would clear a value the student never saw.
        ...(values.govId ? { govId: values.govId } : {}),
        ...(values.address ? { address: values.address } : { address: null }),
      }),
    onSuccess: () => {
      invalidateAfterPause();
      toast.success('Profile updated');
      setPendingValues(null);
      navigate('/student/profile');
    },
    onError: (error) => { setFormErrors([]); applyApiErrors(error); setPendingValues(null); },
  });

  const uploadPhoto = useMutation({
    mutationFn: (file: File) => studentApi.uploadPhoto(profile.data?.id as number, file),
    onSuccess: () => {
      invalidateAfterPause();
      toast.success('Photo updated — your passes are paused until staff re-verify');
      setPhotoFile(null);
      setConfirmingPhoto(false);
    },
    onError: (error) => toast.fromError(error, 'That photo could not be uploaded.'),
  });

  const removePhoto = useMutation({
    mutationFn: () => studentApi.removePhoto(profile.data?.id as number),
    onSuccess: () => {
      invalidateAfterPause();
      toast.success('Photo removed');
      setRemovingPhoto(false);
    },
    onError: (error) => toast.fromError(error, 'That photo could not be removed.'),
  });

  if (profile.isPending) {
    return <div className="surface-card p-[var(--sp-6)]"><SkeletonText lines={8} /></div>;
  }
  if (profile.isError) {
    return <ErrorState error={profile.error} onRetry={() => void profile.refetch()} />;
  }

  /** Which pause-triggering fields this submission actually changes. */
  const changedPausingFields = (values: StudentProfileValues): string[] => {
    const changed: string[] = [];
    const currentDept = profile.data.departmentId ?? '';
    if (String(values.departmentId ?? '') !== String(currentDept)) changed.push('department');
    if (values.govId) changed.push('government ID');
    return changed;
  };

  const onSubmit = (values: StudentProfileValues) => {
    setFormErrors([]);
    // Warning 3. Only when something pausing actually changed - a modal on an
    // address edit would train the student to dismiss it unread.
    if (changedPausingFields(values).length > 0) setPendingValues(values);
    else save.mutate(values);
  };

  const pausingNow = pendingValues ? changedPausingFields(pendingValues) : [];

  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      <Button variant="link" asChild className="self-start">
        <Link to="/student/profile"><ArrowLeft aria-hidden />Back to your profile</Link>
      </Button>

      <PageHeader title="Edit your profile" />

      {/* Warning 2: persistent, above the form, present before any edit. */}
      <section className="flex items-start gap-[var(--sp-3)] rounded-[var(--r-md)]
                          border border-[var(--status-border)] bg-[var(--status-bg)] p-[var(--sp-4)]">
        <AlertTriangle aria-hidden className="mt-[2px] size-5 shrink-0 text-[var(--ink-700)]" />
        <div>
          <h2 className="text-body-md text-[var(--ink-900)]">
            Some changes pause your passes
          </h2>
          <p className="text-small mt-[var(--sp-1)] text-[var(--ink-700)]">
            Your photo, government ID and department are what a guard checks against you at
            the gate. Change any of them and your passes stop scanning until staff
            re-verify. Marked below with a warning symbol.
          </p>
        </div>
      </section>

      <form
        noValidate
        onSubmit={form.handleSubmit(onSubmit)}
        className="surface-card flex flex-col gap-[var(--sp-5)] p-[var(--sp-6)]"
      >
        <FormError messages={formErrors} />

        {/* Warning 1 on each pausing field, via Field's own pausesPass prop. */}
        <Field
          label="Department"
          pausesPass
          hint="Changing this pauses your passes."
          error={form.formState.errors.departmentId?.message}
        >
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
          label="Government ID"
          pausesPass
          hint={
            profile.data.govIdPresent
              ? `We hold ${profile.data.govIdMasked ?? 'one'}. Leave blank to keep it.`
              : 'Optional. Changing this pauses your passes.'
          }
          error={form.formState.errors.govId?.message}
        >
          {({ id, describedBy }) => (
            <Input
              id={id}
              autoComplete="off"
              placeholder={profile.data.govIdPresent ? 'Leave blank to keep the current one' : ''}
              aria-describedby={describedBy}
              invalid={Boolean(form.formState.errors.govId)}
              {...form.register('govId')}
            />
          )}
        </Field>

        <Field
          label="Roll number"
          hint="Does not pause your passes."
          error={form.formState.errors.rollNo?.message}
        >
          {({ id, describedBy }) => (
            <Input id={id} aria-describedby={describedBy}
                   invalid={Boolean(form.formState.errors.rollNo)} {...form.register('rollNo')} />
          )}
        </Field>

        <Field label="Address" error={form.formState.errors.address?.message}>
          {({ id, describedBy }) => (
            <Textarea id={id} rows={3} aria-describedby={describedBy}
                      invalid={Boolean(form.formState.errors.address)} {...form.register('address')} />
          )}
        </Field>

        <div className="flex flex-wrap gap-[var(--sp-3)]">
          <Button type="submit" loading={save.isPending}>Save changes</Button>
          <Button type="button" variant="secondary" asChild>
            <Link to="/student/profile">Cancel</Link>
          </Button>
        </div>
      </form>

      {/* Photo is a separate upload, not part of the form - it posts multipart
          to its own endpoint, and mixing it in would mean one failure rolling
          back the other. Same three warnings apply. */}
      <section className="surface-card flex flex-col gap-[var(--sp-4)] p-[var(--sp-6)]">
        <div className="flex items-center gap-[var(--sp-2)]">
          <AlertTriangle aria-hidden className="size-4 text-[var(--ink-700)]" />
          <h2 className="text-h3 text-[var(--ink-900)]">Photo</h2>
        </div>
        <p className="text-small text-[var(--ink-700)]">
          This is the face a guard compares against you. Changing it pauses your passes.
        </p>

        <FileDropzone
          rule={UPLOAD_RULES.photo}
          file={photoFile}
          onSelect={(file) => { setPhotoFile(file); setConfirmingPhoto(true); }}
          onClear={() => setPhotoFile(null)}
          disabled={uploadPhoto.isPending}
        />

        {profile.data.photoS3Key && (
          <Button
            type="button"
            variant="ghost"
            className="self-start"
            onClick={() => setRemovingPhoto(true)}
          >
            <Trash2 aria-hidden />Remove current photo
          </Button>
        )}
      </section>

      {/* Warning 3, for the form fields. Names exactly what pauses. */}
      <ConfirmDialog
        open={pendingValues !== null}
        onOpenChange={(open) => { if (!open) setPendingValues(null); }}
        title="This will pause your passes"
        description={`You changed your ${pausingNow.join(' and ')}. Every active pass stops scanning at the gate until staff re-verify your profile. You keep the same QR code — nothing is reissued.`}
        confirmLabel="Save and pause"
        loading={save.isPending}
        onConfirm={() => { if (pendingValues) save.mutate(pendingValues); }}
      />

      <ConfirmDialog
        open={confirmingPhoto}
        onOpenChange={(open) => { if (!open) { setConfirmingPhoto(false); setPhotoFile(null); } }}
        title="This will pause your passes"
        description="Your photo is what a guard checks against your face. Replacing it stops every pass from scanning until staff re-verify."
        confirmLabel="Upload and pause"
        loading={uploadPhoto.isPending}
        onConfirm={() => { if (photoFile) uploadPhoto.mutate(photoFile); }}
      />

      <ConfirmDialog
        open={removingPhoto}
        onOpenChange={(open) => { if (!open) setRemovingPhoto(false); }}
        title="Remove your photo?"
        description="Without a photo a guard has nothing to check your face against, and your passes will pause until staff re-verify."
        confirmLabel="Remove"
        destructive
        loading={removePhoto.isPending}
        onConfirm={() => removePhoto.mutate()}
      />
    </div>
  );
}
