import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { Link } from 'react-router';
import { AlertTriangle, UsersRound } from 'lucide-react';
import { Badge, Button } from '@ui/index';
import { DataTable, PageHeader, SearchFilterBar } from '@components/data';
import { EmptyState, ErrorState } from '@components/feedback';
import { authApi } from '@lib/api/services/auth.api';
import { campusApi } from '@lib/api/services/campus.api';
import { authKeys, campusKeys } from '@lib/query/keys';
import { formatDateTime } from '@lib/format/datetime';
import type { UserResponse } from '@/types/auth.types';
import { useAuth } from '@hooks/useAuth';
import { useDebouncedValue } from '@hooks/useDebouncedValue';
import { useUrlPagination } from '@hooks/useUrlPagination';

/**
 * Batch 6 screen 4 — campus admin accounts across the platform.
 *
 * BLOCKER B5 LANDS SQUARELY HERE. `GET /api/auth/users` calls
 * CurrentUser.campusId(), which throws for a Super Admin, so the list is a 403
 * for exactly the role this screen is for. The capability layer already
 * withholds `user:list` when campusId is null, so the table is not attempted;
 * instead the screen shows what a Super Admin CAN do — create an admin against
 * a named campus, which is reachable — and says plainly why the roster is not
 * shown.
 *
 * The alternative was a spinner that never resolves. This is the honest version.
 */
export default function CampusAdminsPage() {
  const { can } = useAuth();
  const { request: pageRequest, setPage } = useUrlPagination(20);
  const [search, setSearch] = useState('');
  const debounced = useDebouncedValue(search, 300);

  const listable = can('user:list');
  const listQuery = { ...pageRequest, role: 'CAMPUS_ADMIN' as const };

  const admins = useQuery({
    queryKey: authKeys.userList(listQuery),
    queryFn: () => authApi.listUsers(listQuery),
    enabled: listable,
  });

  const campuses = useQuery({
    queryKey: campusKeys.list(true),
    queryFn: () => campusApi.list(true),
  });

  const visible = useMemo(() => {
    const items = admins.data?.items ?? [];
    const term = debounced.trim().toLowerCase();
    if (!term) return items;
    return items.filter(
      (user) => user.name.toLowerCase().includes(term) || user.email.toLowerCase().includes(term),
    );
  }, [admins.data, debounced]);

  const columns: ColumnDef<UserResponse, unknown>[] = [
    {
      id: 'name',
      header: 'Name',
      accessorKey: 'name',
      cell: (info) => (
        <div className="min-w-0">
          <p className="text-body-md truncate text-[var(--ink-900)]">{info.row.original.name}</p>
          <p className="text-caption truncate text-[var(--ink-500)]">{info.row.original.email}</p>
        </div>
      ),
    },
    { id: 'campusId', header: 'Campus', cell: (info) => info.row.original.campusId ?? '—' },
    {
      id: 'lastLoginAt',
      header: 'Last sign-in',
      cell: (info) =>
        info.row.original.lastLoginAt ? formatDateTime(info.row.original.lastLoginAt) : 'Never',
    },
    {
      id: 'active',
      header: 'Status',
      cell: (info) => <Badge>{info.row.original.active ? 'Active' : 'Deactivated'}</Badge>,
    },
  ];

  if (!listable) {
    return (
      <div className="flex flex-col gap-[var(--sp-6)]">
        <PageHeader
          title="Campus admins"
          description="Accounts that administer a campus."
          actions={<Button asChild><Link to="/platform/campuses">Go to campuses</Link></Button>}
        />

        <EmptyState
          icon={UsersRound}
          heading="The admin roster is not available to platform accounts"
          description="Listing users is scoped to a single campus server-side, and a platform account belongs to none. Open a campus and add or review its admins from there."
          action={<Button asChild><Link to="/platform/campuses">Open campuses</Link></Button>}
        />

        <section className="surface-card p-[var(--sp-6)]">
          <h2 className="text-h3 text-[var(--ink-900)]">Campuses without an admin</h2>
          <p className="text-caption mt-[var(--sp-1)] text-[var(--ink-500)]">
            Listed from the campus record, which is the only source a platform account
            can read. An admin created outside this screen may exist without being
            recorded here — assigning one from Campuses keeps both in step.
          </p>
          <ul className="mt-[var(--sp-4)] flex flex-col gap-[var(--sp-2)]">
            {(campuses.data ?? [])
              .filter((campus) => campus.adminUserId === null)
              .map((campus) => (
                <li key={campus.code} className="text-body flex items-center gap-[var(--sp-2)] text-[var(--ink-700)]">
                  <AlertTriangle className="size-4 shrink-0 text-[var(--ink-500)]" aria-hidden />
                  {campus.name}
                  <span className="text-mono text-[var(--ink-400)]">{campus.code}</span>
                </li>
              ))}
            {(campuses.data ?? []).every((campus) => campus.adminUserId !== null) ? (
              <li className="text-body text-[var(--ink-500)]">Every campus has an admin assigned.</li>
            ) : null}
          </ul>
        </section>
      </div>
    );
  }

  if (admins.isError) return <ErrorState error={admins.error} onRetry={() => void admins.refetch()} />;

  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      <PageHeader title="Campus admins" description="Accounts that administer a campus." />

      <SearchFilterBar
        value={search} onChange={setSearch}
        placeholder="Search name or email on this page"
        resultCount={visible.length}
      />

      <div className="surface-card overflow-hidden">
        <DataTable
          columns={columns}
          data={visible}
          loading={admins.isPending}
          {...(admins.data ? { page: admins.data } : {})}
          onPageChange={setPage}
          mobilePrimaryColumn="name"
          getRowId={(row) => String(row.id)}
          emptyHeading="No campus admins"
          emptyDescription="Create one from the campuses screen."
        />
      </div>
    </div>
  );
}
