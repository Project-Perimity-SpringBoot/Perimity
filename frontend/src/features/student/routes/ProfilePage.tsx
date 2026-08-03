import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { BadgeCheck, Pencil } from 'lucide-react';
import { Avatar, Button, SkeletonText } from '@ui/index';
import { ErrorState } from '@components/feedback';
import { DescriptionList, PageHeader } from '@components/data';
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

  const profile = useQuery({
    queryKey: profileKeys.myStudent(),
    queryFn: () => studentApi.me(),
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
        <Avatar name={identity?.name ?? 'Student'} src={photo.data?.url} className="size-16" />
        <div className="min-w-0">
          <h2 className="text-h3 truncate text-[var(--ink-900)]">{identity?.name}</h2>
          <p className="text-small truncate text-[var(--ink-500)]">{identity?.email}</p>
        </div>
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
