import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { AlertTriangle, ClipboardCheck, Clock } from 'lucide-react';
import { Avatar, Button } from '@ui/index';
import { DataTable, PageHeader } from '@components/data';
import { Alert, ErrorState } from '@components/feedback';
import { StudentDetailsReviewDrawer } from '@components/approval';
import { studentApi } from '@lib/api/services/user.api';
import { profileKeys } from '@lib/query/keys';
import { formatDateTime, formatWaitingFor, isWaitingTooLong } from '@lib/format/datetime';
import { useUrlPagination } from '@hooks/useUrlPagination';
import type { StudentProfileResponse } from '@/types/user.types';

export default function StudentVerificationPage() {
  const { request, setPage } = useUrlPagination();
  const [selected, setSelected] = useState<StudentProfileResponse | null>(null);
  const [open, setOpen] = useState(false);

  const pending = useQuery({
    queryKey: profileKeys.pendingVerification(request),
    queryFn: () => studentApi.listPendingVerification(request),
  });

  const review = (row: StudentProfileResponse) => { setSelected(row); setOpen(true); };

  const columns: ColumnDef<StudentProfileResponse, unknown>[] = [
    {
      id: 'name',
      header: 'Student',
      accessorFn: (row) => row.displayName ?? row.rollNo ?? `Profile ${row.id}`,
      cell: ({ row }) => {
        const name = row.original.displayName ?? row.original.rollNo ?? `Profile ${row.original.id}`;
        return (
          <span className="flex min-w-0 items-center gap-[var(--sp-3)]">
            <Avatar name={name} />
            <span className="text-body-md min-w-0 truncate text-[var(--ink-900)]">{name}</span>
          </span>
        );
      },
    },
    {
      id: 'rollNo',
      header: 'Roll number',
      accessorFn: (row) => row.rollNo ?? '—',
      cell: ({ row }) => <span className="text-mono">{row.original.rollNo ?? '—'}</span>,
    },
    {
      id: 'department',
      header: 'Department',
      accessorFn: (row) => row.departmentName ?? '—',
      cell: ({ row }) => row.original.departmentName ?? 'Not set',
    },
    {
      id: 'submittedAt',
      header: 'Waiting',
      accessorFn: (row) => row.submittedAt ?? '',
      cell: ({ row }) => {
        const stale = isWaitingTooLong(row.original.submittedAt);
        return (
          <span
            className="inline-flex items-center gap-[var(--sp-2)]"
            title={formatDateTime(row.original.submittedAt)}
          >
            {stale ? (
              <AlertTriangle className="size-4 shrink-0 text-[var(--review-fg)]" aria-hidden />
            ) : (
              <Clock className="size-4 shrink-0 text-[var(--ink-400)]" aria-hidden />
            )}
            <span className={stale ? 'text-body-md text-[var(--review-fg)]' : undefined}>
              {formatWaitingFor(row.original.submittedAt)}
            </span>
          </span>
        );
      },
    },
    {
      id: 'actions',
      header: '',
      cell: ({ row }) => (
        <div className="flex justify-end">
          <Button
            size="sm"
            variant="outline"
            onClick={(e) => { e.stopPropagation(); review(row.original); }}
          >
            Review
          </Button>
        </div>
      ),
    },
  ];

  if (pending.isError) {
    return <ErrorState error={pending.error} onRetry={() => void pending.refetch()} />;
  }

  const total = pending.data?.total ?? 0;
  const waitingTooLong = (pending.data?.items ?? []).filter(
    (row) => isWaitingTooLong(row.submittedAt),
  ).length;

  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      <PageHeader
        breadcrumbs={[{ label: 'Faculty', to: '/faculty' }, { label: 'Student verification' }]}
        title="Student verification"
        description="Review and attest submitted student profiles before a gate pass can be issued."
      />

      {!pending.isPending && total > 0 && (
        <Alert
          tone={waitingTooLong > 0 ? 'warning' : 'info'}
          icon={ClipboardCheck}
          live={false}
          title={`${total} ${total === 1 ? 'student is' : 'students are'} waiting for profile review`}
        >
          {waitingTooLong > 0
            ? `${waitingTooLong} of them have been waiting more than three days.`
            : 'Open a row to check the submitted details against their documents.'}
        </Alert>
      )}

      <div className="surface-panel overflow-hidden">
        <DataTable
          columns={columns}
          data={pending.data?.items ?? []}
          loading={pending.isPending}
          {...(pending.data ? { page: pending.data } : {})}
          onPageChange={setPage}
          mobilePrimaryColumn="name"
          getRowId={(row) => String(row.id)}
          emptyHeading="No pending student verifications"
          emptyDescription="When a student submits their profile for verification, it appears here."
          onRowClick={review}
        />
      </div>

      <StudentDetailsReviewDrawer
        profile={selected}
        open={open}
        onOpenChange={(next) => {
          setOpen(next);
          if (!next) setTimeout(() => setSelected(null), 200);
        }}
      />
    </div>
  );
}
