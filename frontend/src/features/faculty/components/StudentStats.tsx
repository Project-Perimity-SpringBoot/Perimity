import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { ClipboardList, GraduationCap, ImageOff } from 'lucide-react';
import { StatCard } from '@components/data';
import { studentApi, studentImportApi } from '@lib/api/services/user.api';
import { importKeys, profileKeys } from '@lib/query/keys';

/**
 * How many students there are, and what still needs doing about them.
 *
 * ==========================================================================
 * WHY THIS EXISTS
 * ==========================================================================
 * A faculty member imported thirty students and had nowhere to see that it
 * had worked. The overview showed visitor requests and running events - both
 * useful, neither anything to do with the cohort they had just onboarded. The
 * only confirmation was a toast that disappeared after five seconds.
 *
 * ==========================================================================
 * THE PHOTO NUMBER IS DERIVED, AND ONLY FROM RECENT IMPORTS
 * ==========================================================================
 * There is no endpoint for "students with no photo" and this does not invent
 * one. What exists is missingPhotoCount on each import batch, so this sums it
 * across the last few - which answers "did my imports leave anyone without a
 * picture", not "how many students on this campus have no photo". Those are
 * different questions, and the hint text says which one is being answered.
 *
 * If the second question ever needs an honest answer it needs a real count
 * from user-service, not a wider page size here.
 */
export function StudentStats() {
  const total = useQuery({
    queryKey: profileKeys.studentCount(undefined),
    queryFn: () => studentApi.count(),
  });

  const awaiting = useQuery({
    queryKey: profileKeys.pendingVerificationCount(undefined),
    queryFn: () => studentApi.countPendingVerification(),
  });

  /*
   * Ten, not five. The imports panel below shows five; this reads a few more
   * so a photo problem from an earlier batch does not silently drop off the
   * number the moment somebody runs another import.
   */
  const recent = useQuery({
    queryKey: importKeys.list({ page: 0, size: 10 }),
    queryFn: () => studentImportApi.list({ page: 0, size: 10 }),
  });

  const missingPhotos = (recent.data?.items ?? [])
    .reduce((sum, batch) => sum + batch.missingPhotoCount, 0);

  return (
    <div className="grid gap-[var(--sp-4)] sm:grid-cols-3">
      <StatCard
        icon={GraduationCap}
        label="Students"
        value={total.data ?? null}
        loading={total.isPending}
        hint="On this campus"
      />

      {/*
       * Linked only when the number is non-zero. A card reading "0 awaiting
       * verification" that navigates to an empty list is a dead end dressed
       * up as an action.
       */}
      <Linked to={awaiting.data ? '/faculty/students/verification' : undefined}>
        <StatCard
          icon={ClipboardList}
          label="Awaiting verification"
          value={awaiting.data ?? null}
          loading={awaiting.isPending}
          hint="Students who submitted their own details"
        />
      </Linked>

      <Linked to={missingPhotos ? '/faculty/students/import' : undefined}>
        <StatCard
          icon={ImageOff}
          label="No photo yet"
          value={missingPhotos}
          loading={recent.isPending}
          hint="From your recent imports"
        />
      </Linked>
    </div>
  );
}

function Linked({ to, children }: { to?: string | undefined; children: React.ReactNode }) {
  if (!to) {
    return <>{children}</>;
  }
  return (
    <Link
      to={to}
      className="rounded-[var(--r-lg)] transition-shadow focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand-500)] hover:shadow-md"
    >
      {children}
    </Link>
  );
}
