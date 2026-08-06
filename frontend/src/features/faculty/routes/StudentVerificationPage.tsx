import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { AlertTriangle, ClipboardCheck, Clock, Sparkles } from 'lucide-react';
import { Avatar, Button } from '@ui/index';
import { DataTable } from '@components/data';
import { ErrorState } from '@components/feedback';
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
      header: 'Student Profile',
      accessorFn: (row) => row.displayName ?? row.rollNo ?? `Profile ${row.id}`,
      cell: ({ row }) => {
        const name = row.original.displayName ?? row.original.rollNo ?? `Profile ${row.original.id}`;
        return (
          <span className="flex min-w-0 items-center gap-3">
            <Avatar name={name} />
            <span className="min-w-0 truncate font-bold text-slate-900 text-sm">{name}</span>
          </span>
        );
      },
    },
    {
      id: 'rollNo',
      header: 'Roll Number',
      accessorFn: (row) => row.rollNo ?? '—',
      cell: ({ row }) => (
        <span className="font-mono font-bold text-xs text-indigo-700 bg-indigo-50 px-2.5 py-1 rounded-md border border-indigo-100">
          {row.original.rollNo ?? '—'}
        </span>
      ),
    },
    {
      id: 'department',
      header: 'Department',
      accessorFn: (row) => row.departmentName ?? '—',
      cell: ({ row }) => (
        <span className="font-medium text-xs text-slate-700">{row.original.departmentName ?? 'Not set'}</span>
      ),
    },
    {
      id: 'submittedAt',
      header: 'Waiting Time',
      accessorFn: (row) => row.submittedAt ?? '',
      cell: ({ row }) => {
        const stale = isWaitingTooLong(row.original.submittedAt);
        return (
          <span
            className="inline-flex items-center gap-2 text-xs"
            title={formatDateTime(row.original.submittedAt)}
          >
            {stale ? (
              <AlertTriangle className="size-4 shrink-0 text-amber-600" />
            ) : (
              <Clock className="size-4 shrink-0 text-slate-400" />
            )}
            <span className={stale ? 'font-bold text-amber-700' : 'text-slate-600'}>
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
            className="bg-indigo-50 text-indigo-700 border border-indigo-200 hover:bg-indigo-100 font-semibold"
            onClick={(e) => { e.stopPropagation(); review(row.original); }}
          >
            Review Profile
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
    <div className="mx-auto max-w-7xl space-y-6 pb-16">
      {/* Hero Header */}
      <div className="relative overflow-hidden rounded-3xl border border-indigo-100 bg-gradient-to-r from-slate-900 via-indigo-950 to-slate-900 p-8 text-white shadow-xl">
        <div className="flex flex-wrap items-center justify-between gap-6">
          <div className="space-y-2">
            <div className="inline-flex items-center gap-2 rounded-full bg-white/10 px-3 py-1 text-xs font-semibold text-indigo-200 backdrop-blur-md border border-white/10">
              <Sparkles className="size-3.5 text-indigo-300" /> Student Verification Desk
            </div>
            <h1 className="text-3xl font-extrabold text-white sm:text-4xl">Student Profile Verification</h1>
            <p className="text-sm text-indigo-200/90 max-w-xl">
              Review and attest submitted student profiles for gate pass issuance.
            </p>
          </div>
        </div>
      </div>

      {!pending.isPending && total > 0 && (
        <div className="flex items-center justify-between rounded-2xl border border-indigo-100 bg-indigo-50/60 p-4 px-6 text-slate-800 shadow-sm">
          <div className="flex items-center gap-3">
            <div className="flex size-9 items-center justify-center rounded-xl bg-indigo-600 text-white">
              <ClipboardCheck className="size-5" />
            </div>
            <p className="text-sm">
              <strong className="font-bold text-slate-900">
                {total} {total === 1 ? 'student is' : 'students are'} waiting for profile review
              </strong>
              {waitingTooLong > 0 && (
                <span className="text-amber-700 font-semibold ml-2">
                  ({waitingTooLong} waiting &gt; 3 days)
                </span>
              )}
            </p>
          </div>
        </div>
      )}

      <div className="overflow-hidden rounded-2xl border border-slate-200/80 bg-white shadow-sm">
        <DataTable
          columns={columns}
          data={pending.data?.items ?? []}
          loading={pending.isPending}
          {...(pending.data ? { page: pending.data } : {})}
          onPageChange={setPage}
          mobilePrimaryColumn="name"
          getRowId={(row) => String(row.id)}
          emptyHeading="No pending student verifications"
          emptyDescription="When a student submits their profile for verification, it will appear here."
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
