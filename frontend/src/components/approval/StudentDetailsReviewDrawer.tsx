import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Check, ImageOff, X } from 'lucide-react';
import { z } from 'zod';
import {
  Button, Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter,
  DialogHeader, DialogTitle, Field, Textarea,
} from '@ui/index';
import { DescriptionList } from '@components/data';
import { AuthedImage } from '@components/upload';
import { FormError } from '@components/feedback';
import { ProfileVerificationBadge } from '@components/pass/StatusBadge';
import { studentApi } from '@lib/api/services/user.api';
import { profileKeys } from '@lib/query/keys';
import { formatDate, formatDateTime } from '@lib/format/datetime';
import { LIMITS } from '@lib/validation/patterns';
import { useApiFormErrors } from '@hooks/useApiForm';
import { useToast } from '@hooks/useToast';
import { GENDER_LABELS } from '@/types/enums';
import type { StudentProfileResponse } from '@/types/user.types';

/**
 * StudentVerificationDecisionDto. There is no verifiedBy field and there must
 * not be one — the server takes the reviewer from the token. A body naming its
 * own reviewer would let one member of staff record a decision under another's
 * name, which is the whole reason the record exists.
 */
const rejectSchema = z.object({
  remarks: z
    .string()
    .trim()
    .min(1, 'Say what needs changing — the student is shown this')
    .max(LIMITS.verificationRemarks.max, 'Keep it under 500 characters'),
});
type RejectValues = z.infer<typeof rejectSchema>;

const joinPhone = (code: string | null, number: string | null) =>
  number ? `${code ?? ''} ${number}`.trim() : '—';

/**
 * One student's submitted details, and the decision on them.
 *
 * IN components/ RATHER THAN features/faculty FOR THE SAME REASON AS
 * ApprovalDrawer: faculty and campus admins both review these, against the same
 * endpoint with the same server rules. Two copies would drift.
 *
 * ==========================================================================
 * A REJECTION REASON IS MANDATORY
 * ==========================================================================
 * The server's @AssertTrue enforces it. Requiring it here means the student
 * gets a sentence telling them what to fix, instead of a bare "rejected" they
 * can only respond to by resubmitting the same thing — which wastes the
 * reviewer's time as much as the student's.
 *
 * ==========================================================================
 * WHAT IS SHOWN, AND WHAT IS NOT
 * ==========================================================================
 * Everything the student typed, because the job is checking exactly that. The
 * government ID stays masked — the server never sends it in full, and a
 * reviewer confirming a date of birth has no need for twelve digits.
 */
export function StudentDetailsReviewDrawer({
  profile, open, onOpenChange,
}: {
  profile: StudentProfileResponse | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [rejecting, setRejecting] = useState(false);
  const [formErrors, setFormErrors] = useState<string[]>([]);

  /*
   * The profile carries a storage KEY, not a URL, so it has to be signed before
   * it can go in an <img>. Fetched only while the dialog is open — a queue of
   * twenty rows would otherwise mint twenty signed links nobody looks at.
   */
  const photoUrl = useQuery({
    queryKey: profileKeys.photoUrl('student', profile?.id ?? 0),
    queryFn: () => studentApi.photoUrl(profile?.id as number),
    enabled: open && Boolean(profile?.id) && Boolean(profile?.photoS3Key),
    retry: false,
  });

  const { register, handleSubmit, reset, setError, formState: { errors } } = useForm<RejectValues>({
    resolver: zodResolver(rejectSchema),
    defaultValues: { remarks: '' },
  });
  const applyApiErrors = useApiFormErrors<RejectValues>(setError, setFormErrors);

  /*
   * Invalidating profileKeys.all rather than just the queue: the queue, the
   * count badge and any open profile view all move on a decision. A reviewer
   * who approves someone and still sees them listed will click again and get a
   * 409 from the server refusing to decide twice.
   */
  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: profileKeys.all });
  };

  const approve = useMutation({
    mutationFn: () => studentApi.decideVerification(profile?.id as number, { approved: true }),
    onSuccess: () => {
      invalidate();
      toast.success('Verified', 'The student has been told.');
      onOpenChange(false);
    },
    onError: (error) => { setFormErrors([]); applyApiErrors(error); },
  });

  const reject = useMutation({
    mutationFn: (values: RejectValues) =>
      studentApi.decideVerification(profile?.id as number, {
        approved: false,
        remarks: values.remarks,
      }),
    onSuccess: () => {
      invalidate();
      toast.success('Sent back', 'The student can see your note and correct it.');
      setRejecting(false);
      reset();
      onOpenChange(false);
    },
    onError: (error) => { setFormErrors([]); applyApiErrors(error); },
  });

  if (!profile) return null;

  // Only a SUBMITTED profile can be decided; the server returns 409 otherwise.
  const decidable = profile.verificationStatus === 'SUBMITTED';

  return (
    <>
      {/*
        Centered, not a right-hand drawer. A drawer suits something you consult
        alongside the list; this is a decision that wants full attention, and
        centring puts the details and the two buttons in one field of view.

        max-w-2xl rather than the default lg — the details are a two-column
        list, and at lg every value wrapped onto its own line.
      */}
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent side="center" className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>{profile.displayName ?? 'Student details'}</DialogTitle>
            <DialogDescription>
              {profile.submittedAt
                ? `Submitted ${formatDateTime(profile.submittedAt)}`
                : 'Not yet submitted'}
            </DialogDescription>
          </DialogHeader>

          <DialogBody className="flex flex-col gap-[var(--sp-6)]">
            <div className="flex flex-wrap items-center gap-[var(--sp-2)]">
              <ProfileVerificationBadge status={profile.verificationStatus} />
            </div>

            <FormError messages={formErrors} />

            {/* -----------------------------------------------------------
                The passport photo, first and large.

                This is the one thing on the screen that does the gate's job:
                every other value is a fact to read, but the photo is what a
                guard holds against a face. Putting it below the text would put
                the least checkable item first and the most important one after
                a scroll.

                128px because a thumbnail cannot be checked against anything.
               ----------------------------------------------------------- */}
            <section className="flex flex-wrap items-start gap-[var(--sp-6)]">
              <div className="shrink-0">
                {photoUrl.isPending && profile.photoS3Key ? (
                  <div className="size-32 animate-pulse rounded-[var(--r-md)] bg-[var(--surface-sunken)]" />
                ) : (
                  /*
                    AuthedImage fetches through the API client, because in
                    local-storage mode this path needs the reviewer's token and
                    a browser attaches none to an <img>.

                    The fallback says which of the two problems it is. "No
                    photo" should be unreachable now that the server refuses
                    submission without one, so a reviewer seeing it is looking
                    at a record that predates the rule — worth distinguishing
                    from a photo that exists and would not load.
                  */
                  <AuthedImage
                    url={photoUrl.data?.url}
                    alt={`Passport photo of ${profile.displayName ?? 'this student'}`}
                    className="size-32 rounded-[var(--r-md)] border border-[var(--border)]"
                    fallback={
                      <div className="flex size-32 flex-col items-center justify-center gap-[var(--sp-2)] rounded-[var(--r-md)] border border-dashed border-[var(--border-strong)] p-[var(--sp-2)] text-center text-[var(--ink-500)]">
                        <ImageOff className="size-6" aria-hidden />
                        <span className="text-caption">
                          {profile.photoS3Key ? 'Could not load' : 'No photo'}
                        </span>
                      </div>
                    }
                  />
                )}
              </div>

              <p className="text-caption min-w-[12rem] flex-1 text-[var(--ink-500)]">
                Check this face against the details before verifying. A guard
                compares it with the person at the gate.
              </p>
            </section>

            {/*
              Two columns now that the dialog is centred and wider. The list was
              one column in a narrow drawer, which turned eight short values into
              a very tall page and pushed the buttons out of sight.
            */}
            <section>
              <h3 className="text-label mb-[var(--sp-3)] text-[var(--ink-500)]">
                What the student entered
              </h3>
              <DescriptionList
                columns={2}
                items={[
                  { label: 'First name', value: profile.firstName ?? '—' },
                  { label: 'Middle name', value: profile.middleName ?? '—' },
                  { label: 'Last name', value: profile.lastName ?? '—' },
                  { label: 'Date of birth', value: formatDate(profile.dateOfBirth) },
                  {
                    label: 'Gender',
                    value: profile.gender ? GENDER_LABELS[profile.gender] : '—',
                  },
                  {
                    label: 'Phone',
                    value: joinPhone(profile.phoneCountryCode, profile.phoneNumber),
                  },
                  {
                    label: 'Another phone',
                    value: joinPhone(profile.altPhoneCountryCode, profile.altPhoneNumber),
                  },
                  { label: 'Address', value: profile.address ?? '—', wide: true },
                ]}
              />
            </section>

            <section>
              <h3 className="text-label mb-[var(--sp-3)] text-[var(--ink-500)]">
                Already on record
              </h3>
              <DescriptionList
                columns={2}
                items={[
                  { label: 'Roll number', value: profile.rollNo ?? '—' },
                  { label: 'Department', value: profile.departmentName ?? 'Not set' },
                  {
                    label: 'Government ID',
                    value: profile.govIdPresent ? (profile.govIdMasked ?? 'On file') : 'Not given',
                  },
                ]}
              />
            </section>

            {profile.verificationRemarks && (
              <section>
                <h3 className="text-label mb-[var(--sp-2)] text-[var(--ink-500)]">
                  Previous note
                </h3>
                {/* Free text from another user. Rendered as text, never as HTML. */}
                <p className="text-body text-[var(--ink-700)]">{profile.verificationRemarks}</p>
              </section>
            )}

            {!decidable && (
              <p className="text-caption rounded-[var(--r-sm)] bg-[var(--surface-sunken)] px-[var(--sp-3)] py-[var(--sp-2)] text-[var(--ink-500)]">
                These are not waiting for a decision. Only details a student has
                submitted can be approved or sent back.
              </p>
            )}
          </DialogBody>

          {decidable ? (
            /*
              The footer is pinned by DialogFooter's shrink-0 and the capped
              height on DialogContent, so both actions stay visible however long
              the details run. That was the bug: they were rendered all along,
              just below the bottom of the screen.

              The hint sits on the left so the two buttons keep the right edge,
              where every other dialog in the app puts its confirming action.
            */
            <DialogFooter className="justify-between">
              <span className="text-caption hidden text-[var(--ink-500)] sm:inline">
                Verifying records that you checked these.
              </span>
              <span className="flex items-center gap-[var(--sp-2)]">
                <Button variant="secondary" onClick={() => setRejecting(true)}>
                  <X aria-hidden />Send back
                </Button>
                <Button onClick={() => approve.mutate()} loading={approve.isPending}>
                  <Check aria-hidden />Verify
                </Button>
              </span>
            </DialogFooter>
          ) : null}
        </DialogContent>
      </Dialog>

      <Dialog open={rejecting} onOpenChange={(next) => { if (!next) setRejecting(false); }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Send these back?</DialogTitle>
            <DialogDescription>
              The student sees your note and can correct their details.
            </DialogDescription>
          </DialogHeader>

          <form
            onSubmit={(e) => { void handleSubmit((v) => reject.mutate(v))(e); }}
            noValidate
          >
            <DialogBody>
              <FormError messages={formErrors} />
              <Field label="What needs changing" required error={errors.remarks?.message}>
                {({ id, describedBy }) => (
                  <Textarea
                    id={id}
                    rows={4}
                    aria-describedby={describedBy}
                    placeholder="e.g. The date of birth does not match your ID proof."
                    invalid={Boolean(errors.remarks)}
                    {...register('remarks')}
                  />
                )}
              </Field>
            </DialogBody>
            <DialogFooter>
              <Button type="button" variant="ghost" onClick={() => setRejecting(false)}>
                Cancel
              </Button>
              <Button type="submit" loading={reject.isPending}>Send back</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </>
  );
}
