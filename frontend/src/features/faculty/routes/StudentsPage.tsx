import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import type { ColumnDef } from '@tanstack/react-table';
import { BadgeCheck, ImageOff, KeyRound, PlayCircle, Plus } from 'lucide-react';
import { Link } from 'react-router';
import { Badge, Button, Field, NativeSelect, Textarea } from '@ui/index';
import { DataTable, PageHeader, SearchFilterBar } from '@components/data';
import { ConfirmDialog, ErrorState } from '@components/feedback';
import { authApi } from '@lib/api/services/auth.api';
import { passApi } from '@lib/api/services/gatepass.api';
import { departmentApi, studentApi } from '@lib/api/services/user.api';
import { authKeys, departmentKeys, passKeys, profileKeys } from '@lib/query/keys';
import { formatDateTime } from '@lib/format/datetime';
import type { UserResponse } from '@/types/auth.types';
import type { StudentProfileResponse } from '@/types/user.types';
import type { ProfileVerificationStatus } from '@/types/enums';
import { useAuth } from '@hooks/useAuth';
import { useDebouncedValue } from '@hooks/useDebouncedValue';
import { useToast } from '@hooks/useToast';
import { useUrlPagination } from '@hooks/useUrlPagination';
import {
  studentStatusChangeSchema, type StudentStatusChangeValues,
} from '../schemas/faculty.schemas';

/**
 * The student directory, for faculty.
 *
 * ==========================================================================
 * WHY THIS SCREEN EXISTS
 * ==========================================================================
 * Faculty could create students - one at a time and by the hundred - and then
 * had nowhere to look at them. Every other list in the product answers "who is
 * here"; the people faculty actually onboard were the one group with no such
 * screen. The Campus Admin has exactly this for Faculty and Guard accounts and
 * this is the same screen pointed at the one role faculty own.
 *
 * ==========================================================================
 * THE ACCOUNT LIST IS THE SPINE, THE PROFILE IS THE ENRICHMENT
 * ==========================================================================
 * Two services hold half of a student each. auth-service has the account -
 * name, email, whether they can sign in - and user-service has the profile -
 * roll number, department, whether anyone has checked their details.
 *
 * The account list drives the table, because every ACTION on this screen is an
 * account action and because auth-service is the only side that can page a
 * campus's students by role. The profiles are fetched once and joined by
 * userId, which is the only key the two sides share.
 *
 * Nothing here calls an endpoint the server would refuse: UserAdminController's
 * VISIBLE and CREATABLE both map FACULTY to exactly {STUDENT}, so listing,
 * deactivating and reactivating a student are all a faculty member's to do -
 * and a student is the ONLY account they can touch. The screen is not granting
 * anything; it is showing what the rules already allow.
 */
export default function StudentsPage() {
  const { identity } = useAuth();
  const toast = useToast();
  const queryClient = useQueryClient();
  const { request: pageRequest, params, setPage, setFilter } = useUrlPagination(20);

  const departmentParam = params.get('department') ?? '';
  const [search, setSearch] = useState('');
  const debounced = useDebouncedValue(search, 300);
  const [deactivating, setDeactivating] = useState<UserResponse | null>(null);

  const listQuery = { ...pageRequest, role: 'STUDENT' as const };

  const accounts = useQuery({
    queryKey: authKeys.userList(listQuery),
    queryFn: () => authApi.listUsers(listQuery),
  });

  /*
   * activeOnly FALSE, and that is not laziness.
   *
   * This list does two jobs: it fills the filter dropdown AND it is how a
   * department id becomes a department name (see departmentNameById below).
   * Retired departments still have students in them - ProfileGuard refuses a
   * retired department on a NEW selection but deliberately leaves the ones
   * already on a profile alone, because a person's department must not vanish
   * because the campus stopped offering it. Loading active-only would render
   * every one of those students as "Not set" and give nobody a way to filter
   * for them.
   */
  const departments = useQuery({
    queryKey: departmentKeys.list(identity?.campusId ?? undefined, false),
    queryFn: () => departmentApi.list(identity?.campusId ?? undefined, false),
    enabled: identity?.campusId != null,
  });

  /*
   * =======================================================================
   *  THE NAME IS RESOLVED HERE BECAUSE THE SERVER DELIBERATELY OMITS IT
   * =======================================================================
   * /students returns the forDirectory shape, and forDirectory leaves
   * departmentName null on purpose - filling it would be one extra query per
   * row, so twenty rows would cost twenty queries. StudentProfileService says
   * exactly this, and points at the fact that any screen showing the directory
   * has already loaded the department list for its filter.
   *
   * So this screen holds up its end of that bargain. Reading
   * profile.departmentName directly is what the first version did, and every
   * row said "Not set" while the printed pass showed the real department -
   * because the pass is built from a single-profile read, which does carry it.
   */
  const departmentNameById = useMemo(() => {
    const map = new Map<number, string>();
    for (const department of departments.data ?? []) {
      map.set(department.id, department.name);
    }
    return map;
  }, [departments.data]);

  /*
   * Every profile on the campus in one read, keyed by userId.
   *
   * A page-sized fetch would be the tidy thing and it does not work: this
   * table's page is a page of ACCOUNTS, and there is no endpoint that takes a
   * list of user ids and returns their profiles. One call per row would be
   * twenty round trips per page.
   *
   * size 500 is a deliberate ceiling rather than an accident. A campus past it
   * loses the roll number and department columns on the overflow, which is
   * visibly wrong rather than silently wrong, and the fix at that point is a
   * batch endpoint - not a bigger number here.
   */
  const profileQuery = { campusId: identity?.campusId ?? undefined, page: 0, size: 500 };
  const profiles = useQuery({
    queryKey: profileKeys.studentList(profileQuery),
    queryFn: () => studentApi.list(profileQuery),
    enabled: identity?.campusId != null,
  });

  const profileByUser = useMemo(() => {
    const map = new Map<number, StudentProfileResponse>();
    for (const profile of profiles.data?.items ?? []) {
      map.set(profile.userId, profile);
    }
    return map;
  }, [profiles.data]);

  /*
   * Search and the department filter are both client-side, on the current page
   * only, and the caption under the table says so.
   *
   * Not a shortcut: /api/auth/users accepts a role and a page and nothing else.
   * Filtering server-side would mean driving the table off the PROFILE list
   * instead, which can filter by department - and then losing email, sign-in
   * state and every action on this screen, because a profile has none of them.
   * The Campus Admin's Users screen makes the same trade for the same reason.
   */
  const visible = useMemo(() => {
    const items = accounts.data?.items ?? [];
    const term = debounced.trim().toLowerCase();
    const departmentId = departmentParam === '' ? null : Number(departmentParam);

    return items.filter((user) => {
      const profile = profileByUser.get(user.id);

      if (departmentId !== null && profile?.departmentId !== departmentId) {
        return false;
      }
      if (!term) {
        return true;
      }
      return (
        user.name.toLowerCase().includes(term)
        || user.email.toLowerCase().includes(term)
        || (profile?.rollNo ?? '').toLowerCase().includes(term)
      );
    });
  }, [accounts.data, debounced, departmentParam, profileByUser]);

  const statusForm = useForm<StudentStatusChangeValues>({
    resolver: zodResolver(studentStatusChangeSchema),
    defaultValues: { reason: '' },
  });

  const changeStatus = useMutation({
    mutationFn: (values: StudentStatusChangeValues) =>
      authApi.changeUserStatus(deactivating?.id as number, {
        active: !(deactivating?.active ?? true),
        reason: values.reason,
        changedBy: identity?.userId as number,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: authKeys.users() });
      toast.success(deactivating?.active ? 'Student deactivated' : 'Student reactivated');
      setDeactivating(null);
      statusForm.reset();
    },
    onError: (error) => toast.fromError(error, 'That account could not be updated.'),
  });

  const resetPassword = useMutation({
    mutationFn: (email: string) => authApi.requestPasswordReset({ email }),
    onSuccess: () =>
      toast.success('Reset email sent', 'The link goes to the student, not to you.'),
    onError: (error) => toast.fromError(error, 'That reset could not be requested.'),
  });

  /*
   * =======================================================================
   *  RESUME A HELD PASS
   * =======================================================================
   * A pass pauses automatically when a checked profile is edited, and until
   * now nothing in the product could move one back - passApi.changeStatus
   * existed and had no caller on any screen, so a student who changed their
   * photo lost their pass permanently.
   *
   * Approving their profile now resumes it on the server, which covers the
   * normal path. This button covers everything else: a pass held before that
   * fix existed, a student whose approval landed while gatepass was down, or a
   * pause nobody can now explain.
   *
   * TWO CALLS, ON DEMAND, and not one per row. The table lists ACCOUNTS and a
   * pass belongs to a holder, so knowing which rows have a paused pass would
   * mean a request per row on every page render. Fetching this student's
   * passes when somebody actually asks is one call instead of twenty.
   */
  const resumePasses = useMutation({
    mutationFn: async (user: UserResponse) => {
      const held = (await passApi.byHolder(user.id)).filter((p) => p.status === 'PAUSED');

      for (const pass of held) {
        // No changedBy: unlike the account-status call above, this DTO takes the
        // actor from the token server-side, so sending one would be ignored at
        // best and a claim about who acted at worst.
        await passApi.changeStatus(pass.id, {
          targetStatus: 'ACTIVE',
          reason: 'Resumed by faculty from the student list',
        });
      }
      return held.length;
    },
    onSuccess: (count, user) => {
      if (count === 0) {
        // Said plainly rather than reported as success. "Resumed 0 passes" reads
        // as though something happened; nothing did, and the reason matters -
        // this student's pass was never held in the first place.
        toast.success(`${user.name} has no paused pass`, 'Nothing needed resuming.');
        return;
      }
      void queryClient.invalidateQueries({ queryKey: passKeys.byHolder(user.id) });
      toast.success(
        count === 1 ? 'Pass resumed' : `${count} passes resumed`,
        'The same QR code works again — nothing was reissued.',
      );
    },
    onError: (error) => toast.fromError(error, 'That pass could not be resumed.'),
  });

  const columns: ColumnDef<UserResponse, unknown>[] = [
    {
      id: 'name',
      header: 'Student',
      accessorKey: 'name',
      cell: (info) => (
        <div className="min-w-0">
          <p className="text-body-md truncate text-[var(--ink-900)]">{info.row.original.name}</p>
          <p className="text-caption truncate text-[var(--ink-500)]">{info.row.original.email}</p>
        </div>
      ),
    },
    {
      id: 'rollNo',
      header: 'Roll number',
      cell: (info) => {
        const profile = profileByUser.get(info.row.original.id);
        return profile?.rollNo
          ? <span className="font-mono text-small">{profile.rollNo}</span>
          : <span className="text-[var(--ink-400)]">—</span>;
      },
    },
    {
      id: 'department',
      header: 'Department',
      cell: (info) => {
        const profile = profileByUser.get(info.row.original.id);
        const name = profile?.departmentId == null
          ? null
          // departmentName first anyway: it is null on THIS endpoint but
          // populated on every single-profile read, and a column that reads
          // whichever is present survives the server filling it in later.
          : profile.departmentName ?? departmentNameById.get(profile.departmentId) ?? null;

        return name ?? <span className="text-[var(--ink-400)]">Not set</span>;
      },
    },
    {
      id: 'details',
      header: 'Details',
      cell: (info) => {
        const profile = profileByUser.get(info.row.original.id);
        if (!profile) {
          // No profile row at all. Rare and worth showing rather than blanking:
          // it means the account exists and nothing in user-service knows about
          // it, which is a thing somebody has to fix.
          return <Badge tone="brand">No profile</Badge>;
        }
        return (
          <div className="flex flex-wrap items-center gap-[var(--sp-2)]">
            <Badge tone={VERIFICATION_TONE[profile.verificationStatus]}>
              {VERIFICATION_LABEL[profile.verificationStatus]}
            </Badge>
            {/* The photo is the one field that does the access-control job -
                a guard holds it against the face in front of them - so its
                absence is called out here rather than left to be discovered. */}
            {profile.photoS3Key === null && (
              <span
                className="text-caption inline-flex items-center gap-[var(--sp-1)] text-[var(--ink-500)]"
                title="No passport photo yet"
              >
                <ImageOff className="size-3.5" aria-hidden />
                No photo
              </span>
            )}
          </div>
        );
      },
    },
    {
      id: 'status',
      header: 'Account',
      cell: (info) => {
        const user = info.row.original;
        return (
          <div className="min-w-0">
            <Badge>
              {!user.active
                ? 'Deactivated'
                : user.locked
                  ? 'Locked'
                  : user.mustChangePassword
                    ? 'Password pending'
                    : 'Active'}
            </Badge>
            <p className="text-caption mt-[var(--sp-1)] truncate text-[var(--ink-500)]">
              {user.lastLoginAt ? formatDateTime(user.lastLoginAt) : 'Never signed in'}
            </p>
          </div>
        );
      },
    },
    {
      id: 'actions',
      header: '',
      cell: (info) => {
        const user = info.row.original;
        const profile = profileByUser.get(user.id);

        return (
          <div className="flex justify-end gap-[var(--sp-2)]">
            {/* Only offered when there is actually a decision waiting. A link
                to the queue for a student nobody has to check is a click that
                ends on a screen where they do not appear. */}
            {profile?.verificationStatus === 'SUBMITTED' && (
              <Button size="sm" variant="ghost" asChild>
                <Link to="/faculty/students/verification">
                  <BadgeCheck aria-hidden />Check
                </Link>
              </Button>
            )}
            <Button
              size="sm"
              variant="ghost"
              onClick={() => resumePasses.mutate(user)}
              loading={resumePasses.isPending && resumePasses.variables?.id === user.id}
              title="Release any pass held after a profile change"
            >
              <PlayCircle aria-hidden />Resume pass
            </Button>
            <Button
              size="sm"
              variant="ghost"
              onClick={() => resetPassword.mutate(user.email)}
              loading={resetPassword.isPending && resetPassword.variables === user.email}
            >
              <KeyRound aria-hidden />Reset
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setDeactivating(user)}>
              {user.active ? 'Deactivate' : 'Reactivate'}
            </Button>
          </div>
        );
      },
    },
  ];

  if (identity?.campusId == null) {
    return (
      <ErrorState
        error={new Error('Your account has no campus, so there is no student list to show.')}
        onRetry={() => window.location.reload()}
      />
    );
  }

  if (accounts.isError) {
    return <ErrorState error={accounts.error} onRetry={() => void accounts.refetch()} />;
  }

  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      <PageHeader
        title="Students"
        description="Every student account on this campus. Deactivated accounts keep their passes and entry history."
        actions={
          <Button asChild>
            <Link to="/faculty/students/new"><Plus aria-hidden />Add student</Link>
          </Button>
        }
      />

      <SearchFilterBar
        value={search}
        onChange={setSearch}
        placeholder="Search name, email or roll number on this page"
        resultCount={visible.length}
        filters={
          <NativeSelect
            aria-label="Filter by department"
            className="sm:w-56"
            value={departmentParam}
            onChange={(event) => setFilter('department', event.target.value || null)}
          >
            <option value="">All departments</option>
            {(departments.data ?? []).map((department) => (
              <option key={department.id} value={department.id}>{department.name}</option>
            ))}
          </NativeSelect>
        }
      />

      <div className="surface-card overflow-hidden">
        <DataTable
          columns={columns}
          data={visible}
          loading={accounts.isPending}
          {...(accounts.data ? { page: accounts.data } : {})}
          onPageChange={setPage}
          mobilePrimaryColumn="name"
          getRowId={(row) => String(row.id)}
          emptyHeading="No students match"
          emptyDescription="Clear the filters, or add a student."
        />
      </div>

      <p className="text-caption text-[var(--ink-500)]">
        Search and the department filter apply to the current page only — this backend
        has no server-side search, so widen the page size to look further.
      </p>

      <ConfirmDialog
        open={deactivating !== null}
        onOpenChange={(open) => { if (!open) { setDeactivating(null); statusForm.reset(); } }}
        title={
          deactivating?.active
            ? `Deactivate ${deactivating.name}?`
            : `Reactivate ${deactivating?.name ?? ''}?`
        }
        description={
          deactivating?.active
            ? 'They can no longer sign in. Nothing is deleted — their passes, entries and audit trail stay intact. Deactivating the account does NOT revoke a pass already in their pocket; revoke that separately if they must not get through the gate.'
            : 'They will be able to sign in again with their existing password.'
        }
        confirmLabel={deactivating?.active ? 'Deactivate' : 'Reactivate'}
        destructive={deactivating?.active ?? false}
        loading={changeStatus.isPending}
        confirmDisabled={!statusForm.watch('reason')}
        onConfirm={() => void statusForm.handleSubmit((values) => changeStatus.mutate(values))()}
      >
        <Field
          label="Reason"
          required
          error={statusForm.formState.errors.reason?.message}
          hint="Recorded in the audit log."
        >
          {({ id, describedBy }) => (
            <Textarea
              id={id}
              rows={3}
              aria-describedby={describedBy}
              invalid={Boolean(statusForm.formState.errors.reason)}
              {...statusForm.register('reason')}
            />
          )}
        </Field>
      </ConfirmDialog>
    </div>
  );
}

const VERIFICATION_LABEL: Readonly<Record<ProfileVerificationStatus, string>> = {
  DRAFT: 'Not submitted',
  SUBMITTED: 'Awaiting check',
  VERIFIED: 'Verified',
  REJECTED: 'Sent back',
};

/*
 * Only the two tones Badge actually has that mean anything here - it also
 * offers daily/event/visitor, which are pass types and would be a lie on a
 * profile. 'brand' is the one that draws the eye, so it goes on the one state
 * that needs somebody to act.
 */
const VERIFICATION_TONE: Readonly<Record<ProfileVerificationStatus, 'neutral' | 'brand'>> = {
  DRAFT: 'neutral',
  SUBMITTED: 'brand',
  VERIFIED: 'neutral',
  REJECTED: 'neutral',
};
