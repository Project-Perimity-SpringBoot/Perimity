import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router';
import { AlertTriangle, ArrowLeft, Trash2 } from 'lucide-react';
import { Button, SkeletonText } from '@ui/index';
import { ConfirmDialog, ErrorState } from '@components/feedback';
import { DescriptionList, PageHeader } from '@components/data';
import { FileDropzone } from '@components/upload';
import { studentApi } from '@lib/api/services/user.api';
import { passKeys, profileKeys } from '@lib/query/keys';
import { UPLOAD_RULES } from '@lib/validation/patterns';
import { useToast } from '@hooks/useToast';

/**
 * Profile edit — the photo, and nothing else.
 *
 * ==========================================================================
 * WHY A STUDENT MAY CHANGE THEIR PHOTO AND NOTHING ELSE ON THIS PAGE
 * ==========================================================================
 * The test is not "is this field sensitive", it is WHO OWNS THE FACT.
 *
 *   photo          the student's own face          -> theirs to change
 *   government ID  the state's, checked by staff   -> not theirs to rewrite
 *   roll number    assigned by the institution     -> not theirs to choose
 *   department     enrolment, not a preference     -> not theirs to reassign
 *
 * The three institutional fields used to be editable here. A student could
 * silently reassign their own department, rewrite the government ID a member of
 * staff had verified against a physical document, and set a roll number that
 * collides with a classmate's - the column carries uk_student_campus_roll, so
 * that last one fails with a message about somebody else's data.
 *
 * The government ID case is the sharpest: that number is only worth anything
 * BECAUSE someone checked it. Letting the subject of the check rewrite it
 * afterwards makes the verification decorative.
 *
 * They are shown, not hidden. A student needs to see what is on file - that is
 * how a mistyped digit gets noticed - and needs somewhere to go when it is
 * wrong, which is the line under the values.
 *
 * ==========================================================================
 * THE PREVIOUS VERSION OF THIS PAGE WAS FACTUALLY WRONG
 * ==========================================================================
 * Its banner and field labels claimed:
 *
 *   department    "pauses pass"                 -> it does NOT
 *   roll number   "Does not pause your passes"  -> it DOES
 *
 * StudentProfileService.update adds to sensitiveChanges for exactly three
 * fields - rollNo, govId, photoS3Key - and department is set outside that
 * block. A student following the old labels would change their roll number
 * believing it was safe and be refused at the gate, which is the precise
 * failure the warnings existed to prevent.
 *
 * With only the photo editable here, the warning is finally a single true
 * statement instead of a list that has to be kept in sync with the server.
 *
 * ==========================================================================
 * STILL TRUE, AND STILL A GAP
 * ==========================================================================
 * The SERVER continues to accept a crafted PUT /students/{id} from a student
 * changing their own roll number or government ID - requireSelfOrStaff permits
 * the holder. This page no longer offers it; that is a UI-honesty fix, not an
 * authorisation one. Closing it properly means splitting StudentProfileUpdateDto
 * so a self-edit cannot carry institutional fields.
 */
export default function ProfileEditPage() {
  const toast = useToast();
  const queryClient = useQueryClient();

  const [photoFile, setPhotoFile] = useState<File | null>(null);
  const [confirmingPhoto, setConfirmingPhoto] = useState(false);
  const [removingPhoto, setRemovingPhoto] = useState(false);

  const profile = useQuery({
    queryKey: profileKeys.myStudent(),
    queryFn: () => studentApi.me(),
  });

  const invalidateAfterPause = () => {
    void queryClient.invalidateQueries({ queryKey: profileKeys.myStudent() });
    // Passes may have moved to PAUSED server-side. Without this the banner does
    // not appear until a reload, which is the one moment it needs to.
    void queryClient.invalidateQueries({ queryKey: passKeys.all });
  };

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

  const p = profile.data;

  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      <Button variant="link" asChild className="self-start">
        <Link to="/student/profile"><ArrowLeft aria-hidden />Back to your profile</Link>
      </Button>

      <PageHeader
        title="Change your photo"
        description="The only thing on your profile you can change yourself."
      />

      {/* One warning, and now a true one: the photo is the only pausing field
          a student can reach from this page. */}
      <section className="flex items-start gap-[var(--sp-3)] rounded-[var(--r-md)]
                          border border-[var(--status-border)] bg-[var(--status-bg)] p-[var(--sp-4)]">
        <AlertTriangle aria-hidden className="mt-[2px] size-5 shrink-0 text-[var(--ink-700)]" />
        <div>
          <h2 className="text-body-md text-[var(--ink-900)]">
            Changing your photo pauses your passes
          </h2>
          <p className="text-small mt-[var(--sp-1)] text-[var(--ink-700)]">
            A guard compares this picture against your face at the gate, so every active
            pass stops scanning until staff re-verify you. You keep the same QR code —
            nothing is reissued.
          </p>
        </div>
      </section>

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

        {p.photoS3Key && (
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

      {/* Read-only, because these are the institution's facts, not the
          student's. Shown rather than hidden so a wrong value can be noticed. */}
      <section className="surface-card flex flex-col gap-[var(--sp-4)] p-[var(--sp-6)]">
        <h2 className="text-h3 text-[var(--ink-900)]">Set by your institution</h2>

        <DescriptionList
          items={[
            { label: 'Department', value: p.departmentName ?? 'Not set' },
            { label: 'Roll number', value: p.rollNo ?? '—' },
            {
              label: 'Government ID',
              value: p.govIdPresent ? (p.govIdMasked ?? '••••••••') : 'None on file',
            },
          ]}
        />

        <p className="text-caption text-[var(--ink-500)]">
          These are set when you are enrolled and cannot be changed here. If any of them is
          wrong, ask the faculty member who enrolled you to correct it — changing the
          government ID or roll number pauses your passes, so it is done by staff with a
          reason recorded.
        </p>
      </section>

      <p className="text-caption text-[var(--ink-500)]">
        Your name, date of birth, address and phone numbers are on{' '}
        <Link className="underline" to="/student/profile/details">My details</Link>, because
        faculty check those.
      </p>

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
