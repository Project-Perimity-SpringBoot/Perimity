import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { BadgeCheck, Pencil } from 'lucide-react';
import { Avatar, Button, SkeletonText } from '@ui/index';
import { ErrorState } from '@components/feedback';
import { NotFoundError } from '@lib/api/errors';
import { DescriptionList, PageHeader } from '@components/data';
import { AuthedImage } from '@components/upload';
import { ProfileVerificationBadge } from '@components/pass/StatusBadge';
import { studentApi } from '@lib/api/services/user.api';
import { passApi } from '@lib/api/services/gatepass.api';
import { profileKeys, passKeys } from '@lib/query/keys';
import { useAuth } from '@hooks/useAuth';
import { PausedBanner } from '../components/PausedBanner';

/**
 * Phase 3 screen 6 — profile, read-only.
 *
 * ==========================================================================
 * THE GOVERNMENT ID IS NEVER SHOWN IN FULL
 * ==========================================================================
 * The server sends govIdMasked ("********9012") and govIdPresent. The real
 * value never leaves user-service — not to this screen, not to staff, not in
 * any DTO. So there is nothing here to accidentally log, screenshot or leak,
 * and the masking is not a UI decision that a later change could undo.
 *
 * The tick means "we hold one", not "we verified it". Document verification is
 * a separate thing on its own screen, and conflating them would tell a student
 * their ID had been checked when it had not.
 *
 * NO SEMESTER FIELD. It does not exist on the entity, the DTO, or anywhere in
 * this product.
 */
export default function ProfilePage() {
  const { identity } = useAuth();

  // retry: false - a 404 here is a real answer ("no profile yet"), not a
  // transient failure, and retrying it three times only delays the screen that
  // tells the student what to do.
  const profile = useQuery({
    queryKey: profileKeys.myStudent(),
    queryFn: () => studentApi.me(),
    retry: false,
  });

  // Only so the paused banner can render here too - it belongs on every
  // student screen, and profile is where someone lands after being told a
  // change paused their pass.
  const passes = useQuery({
    queryKey: passKeys.mine(),
    queryFn: () => passApi.mine(),
  });

  const photo = useQuery({
    queryKey: profileKeys.photoUrl('student', profile.data?.id ?? 0),
    queryFn: () => studentApi.photoUrl(profile.data?.id as number),
    enabled: profile.data?.photoS3Key != null,
  });

  if (profile.isPending) {
    return <div className="surface-card p-[var(--sp-6)]"><SkeletonText lines={8} /></div>;
  }

  /*
   * A 404 means the account has no profile row yet, which is a state plenty of
   * student accounts are in: the account lives in auth-service and the profile
   * in user-service, and only the faculty Add Student screen ever created the
   * second one.
   *
   * "Not found. No student profile exists for account 21" is accurate and
   * useless — it reads like a broken system and gives the student nothing to
   * do. Saving the details form creates the row, so point at it.
   */
  if (profile.error instanceof NotFoundError) {
    return (
      <div className="surface-card flex flex-col items-start gap-[var(--sp-4)] p-[var(--sp-6)]">
        <div>
          <h1 className="text-h2 text-[var(--ink-900)]">Your profile is not set up yet</h1>
          <p className="text-body mt-[var(--sp-2)] text-[var(--ink-500)]">
            Fill in your details and they will be sent to faculty to check. You need
            this before a pass can be issued to you.
          </p>
        </div>
        <Button asChild>
          <Link to="/student/profile/details">Fill in my details</Link>
        </Button>
      </div>
    );
  }

  if (profile.isError) {
    return <ErrorState error={profile.error} onRetry={() => void profile.refetch()} />;
  }

  const p = profile.data;

  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      <PausedBanner passes={passes.data ?? []} />

      <PageHeader
        title="Your profile"
        description="What a guard sees when your pass is scanned."
        actions={
          <Button asChild>
            <Link to="/student/profile/edit"><Pencil aria-hidden />Edit</Link>
          </Button>
        }
      />

      <section className="surface-card flex items-center gap-[var(--sp-4)] p-[var(--sp-6)]">
        {/*
          AuthedImage rather than Avatar's src. In local-storage mode the photo
          URL sits behind the JWT filter, and a browser sends no Authorization
          header with an image request — so Avatar's img always failed here and
          quietly fell back to initials. That fallback is exactly why the broken
          photo went unnoticed for so long: it looked like a deliberate design.

          Avatar is still the fallback, so a student with no photo sees the same
          initials circle as before.
        */}
        <AuthedImage
          url={photo.data?.url}
          alt="Your profile photo"
          className="size-16 rounded-[var(--r-circle)]"
          fallback={<Avatar name={identity?.name ?? 'Student'} className="size-16" />}
        />
        <div className="min-w-0">
          <h2 className="text-h3 truncate text-[var(--ink-900)]">{identity?.name}</h2>
          <p className="text-small truncate text-[var(--ink-500)]">{identity?.email}</p>
        </div>
      </section>

      {/* ---------------------------------------------------------------
          Verification state.

          A student who submitted their details three days ago has one
          question — has anyone looked yet — and this is the page they open
          to ask it. Leaving the answer only on the edit form meant the
          status lived behind a button labelled for editing.

          Each state carries the next action, because a status with no verb
          is just a label. REJECTED shows the reviewer's note here rather
          than only on the form: it is the reason the student needs to act.
         --------------------------------------------------------------- */}
      <section className="surface-card flex flex-wrap items-center gap-[var(--sp-3)] p-[var(--sp-4)]">
        <ProfileVerificationBadge status={p.verificationStatus} />

        <p className="text-small min-w-0 flex-1 text-[var(--ink-500)]">
          {p.verificationStatus === 'VERIFIED'
            && 'Your details have been checked by faculty.'}
          {p.verificationStatus === 'SUBMITTED'
            && 'Faculty are checking your details. Nothing to do for now.'}
          {p.verificationStatus === 'DRAFT'
            && 'Your details have not been sent for checking yet.'}
          {p.verificationStatus === 'REJECTED' && (
            <>
              Faculty asked for changes
              {/* Free text written by another user. Rendered as text, never HTML. */}
              {p.verificationRemarks ? `: ${p.verificationRemarks}` : '.'}
            </>
          )}
        </p>

        {p.verificationStatus !== 'SUBMITTED' && (
          <Button variant="secondary" asChild>
            <Link to="/student/profile/details">
              {p.verificationStatus === 'VERIFIED' ? 'View details' : 'Fill in details'}
            </Link>
          </Button>
        )}
      </section>

      <section className="surface-card p-[var(--sp-6)]">
        <DescriptionList
          items={[
            { label: 'Roll number', value: p.rollNo ?? '—' },
            { label: 'Department', value: p.departmentName ?? 'Not set' },
            {
              label: 'Government ID',
              value: p.govIdPresent ? (
                <span className="inline-flex items-center gap-[var(--sp-2)]">
                  {/* Masked, always. The full value is not in this response. */}
                  <span className="text-mono">{p.govIdMasked ?? '••••••••'}</span>
                  <BadgeCheck aria-hidden className="size-4 text-[var(--ink-700)]" />
                  <span className="text-caption text-[var(--ink-500)]">on file</span>
                </span>
              ) : (
                'Not provided'
              ),
            },
            { label: 'Address', value: p.address ?? '—', wide: true },
          ]}
        />
      </section>

      <p className="text-caption text-[var(--ink-500)]">
        Changing your name, photo, government ID or department pauses your passes until
        staff re-verify them. Your roll number and address do not.
      </p>
    </div>
  );
}
