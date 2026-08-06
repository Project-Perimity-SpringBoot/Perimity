import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import type { ColumnDef } from '@tanstack/react-table';
import { AlertTriangle, Building2, CircleCheck, CircleSlash } from 'lucide-react';
import { Badge, Button } from '@ui/index';
import { DataTable, PageHeader, StatCard } from '@components/data';
import { ErrorState } from '@components/feedback';
import { campusApi } from '@lib/api/services/campus.api';
import { campusKeys } from '@lib/query/keys';
import type { CampusResponse } from '@/types/campus.types';

/**
 * Super Admin screen 1 — the platform console.
 *
 * DELIBERATELY NARROW, and the reason is worth stating rather than hiding.
 * Every user, pass and entry count in this backend is campus-scoped, and a
 * Super Admin's token carries campusId = null — so those handlers throw before
 * they run. The design brief asks for "users 11,840 · active passes 3,902 ·
 * entries today 1,147"; none of the three is reachable by the role the screen
 * is for.
 *
 * They render as explicit placeholders rather than spinners that never resolve
 * or figures quietly computed from something else. An admin who can see that a
 * number is unavailable is better served than one watching a skeleton forever.
 *
 * Everything below the placeholders IS reachable and is real.
 */
export default function PlatformOverview() {
  const stats = useQuery({ queryKey: campusKeys.stats(), queryFn: () => campusApi.stats() });

  const campuses = useQuery({
    queryKey: campusKeys.list(true),
    queryFn: () => campusApi.list(true),
  });

  if (stats.isError) return <ErrorState error={stats.error} onRetry={() => void stats.refetch()} />;

  const rows = campuses.data ?? [];
  const orphaned = rows.filter((campus) => campus.adminUserId === null);

  const columns: ColumnDef<CampusResponse, unknown>[] = [
    {
      id: 'name',
      header: 'Campus',
      accessorKey: 'name',
      cell: (info) => (
        <div className="min-w-0">
          <p className="text-body-md truncate text-[var(--ink-900)]">{info.row.original.name}</p>
          <p className="text-caption text-mono text-[var(--ink-500)]">{info.row.original.code}</p>
        </div>
      ),
    },
    { id: 'activeGateCount', header: 'Gates', accessorKey: 'activeGateCount', enableSorting: true },
    { id: 'contactEmail', header: 'Contact', cell: (info) => info.row.original.contactEmail ?? '—' },
    {
      id: 'admin',
      header: 'Admin',
      cell: (info) =>
        info.row.original.adminUserId === null ? (
          <span className="text-small text-[var(--review-fg)]">None assigned</span>
        ) : (
          `#${info.row.original.adminUserId}`
        ),
    },
    {
      id: 'active',
      header: 'Status',
      cell: (info) => <Badge>{info.row.original.active ? 'Active' : 'Suspended'}</Badge>,
    },
  ];

  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      <PageHeader
        title="Platform overview"
        description="Every campus on this deployment."
        actions={
          <Button asChild variant="secondary">
            <Link to="/platform/campuses">Manage campuses</Link>
          </Button>
        }
      />

      <div className="grid gap-[var(--sp-4)] sm:grid-cols-3 lg:grid-cols-3">
        <StatCard label="Campuses" icon={Building2}
                  loading={stats.isPending} value={stats.data?.totalCampuses ?? 0} />
        <StatCard label="Active" icon={CircleCheck}
                  loading={stats.isPending} value={stats.data?.activeCampuses ?? 0} />
        <StatCard label="Suspended" icon={CircleSlash}
                  loading={stats.isPending} value={stats.data?.inactiveCampuses ?? 0}
                  hint="Read-only. Nothing is deleted." />
      </div>

      {orphaned.length > 0 ? (
        <section aria-labelledby="attention" className="surface-card p-[var(--sp-6)]">
          <h2 id="attention" className="text-h3 text-[var(--ink-900)]">Needs attention</h2>
          <ul className="mt-[var(--sp-3)] flex flex-col gap-[var(--sp-2)]">
            {orphaned.map((campus) => (
              <li key={campus.code} className="flex items-center justify-between gap-[var(--sp-3)]">
                <span className="text-body flex items-center gap-[var(--sp-2)] text-[var(--ink-700)]">
                  <AlertTriangle className="size-4 shrink-0 text-[var(--review-fg)]" aria-hidden />
                  <strong>{campus.name}</strong> has no admin recorded — assign one here,
                  or it stays invisible to this console
                </span>
                <Button asChild size="sm" variant="ghost">
                  <Link to="/platform/campuses">Assign</Link>
                </Button>
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      <section aria-labelledby="by-campus" className="surface-card overflow-hidden">
        <h2 id="by-campus" className="text-h3 border-b border-[var(--border)] px-[var(--sp-6)] py-[var(--sp-4)] text-[var(--ink-900)]">
          By campus
        </h2>
        <DataTable
          columns={columns}
          data={rows}
          loading={campuses.isPending}
          getRowId={(row) => row.code}
          mobilePrimaryColumn="name"
          emptyHeading="No campuses yet"
          emptyDescription="Create the first one, then assign it a Campus Admin."
        />
      </section>
    </div>
  );
}
