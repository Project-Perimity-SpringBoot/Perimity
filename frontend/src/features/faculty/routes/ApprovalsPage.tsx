import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { Filter, UserCheck } from 'lucide-react';
import { Button } from '@ui/index';
import { DataTable } from '@components/data';
import { ErrorState } from '@components/feedback';
import { RequestStatusBadge } from '@components/pass';
import { ApprovalDrawer } from '@components/approval';
import { visitorRequestApi } from '@lib/api/services/gatepass.api';
import { requestKeys } from '@lib/query/keys';
import { formatDateTime, formatValidity } from '@lib/format/datetime';
import { REQUEST_STATUSES, type RequestStatus } from '@/types/enums';
import type { VisitorRequestResponse } from '@/types/gatepass.types';
import { useUrlPagination } from '@hooks/useUrlPagination';

const isRequestStatus = (value: string): value is RequestStatus =>
  (REQUEST_STATUSES as readonly string[]).includes(value);

export default function ApprovalsPage() {
  const { request: pageRequest, params, setPage, setFilter } = useUrlPagination(20);
  const statusParam = params.get('status') ?? 'PENDING';
  const status: RequestStatus = isRequestStatus(statusParam) ? statusParam : 'PENDING';
  const [selected, setSelected] = useState<VisitorRequestResponse | null>(null);

  const queue = useQuery({
    queryKey: requestKeys.myQueue(status, pageRequest),
    queryFn: () => visitorRequestApi.myQueue(status, pageRequest),
  });

  const columns: ColumnDef<VisitorRequestResponse, unknown>[] = [
    {
      id: 'visitorName',
      header: 'Visitor Details',
      accessorKey: 'visitorName',
      cell: (info) => (
        <span className="flex items-center gap-3 min-w-0">
          <span className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-indigo-50 font-bold text-xs text-indigo-700 border border-indigo-100">
            {info.row.original.visitorName?.charAt(0) ?? 'V'}
          </span>
          <span className="block min-w-0">
            <span className="block truncate font-bold text-slate-900 text-sm">
              {info.row.original.visitorName}
            </span>
            <span className="block truncate font-mono text-xs text-slate-500">
              {info.row.original.visitorEmail}
            </span>
          </span>
        </span>
      ),
    },
    {
      id: 'purpose',
      header: 'Purpose of Visit',
      accessorKey: 'purpose',
      cell: (info) => (
        <span className="line-clamp-2 max-w-[35ch] text-xs text-slate-600 bg-slate-50 p-2 rounded-lg border border-slate-100 font-medium">
          {info.row.original.purpose}
        </span>
      ),
    },
    {
      id: 'visitFrom',
      header: 'Visit Validity',
      accessorKey: 'visitFrom',
      cell: (info) => (
        <span className="font-semibold text-xs text-slate-700">
          {formatValidity(info.row.original.visitFrom, info.row.original.visitTo)}
        </span>
      ),
    },
    {
      id: 'createdAt',
      header: 'Requested On',
      accessorKey: 'createdAt',
      cell: (info) => (
        <span className="font-mono text-xs text-slate-500">
          {formatDateTime(info.row.original.createdAt)}
        </span>
      ),
    },
    {
      id: 'status',
      header: 'Status',
      cell: (info) => <RequestStatusBadge status={info.row.original.status} />,
    },
  ];

  if (queue.isError) return <ErrorState error={queue.error} onRetry={() => void queue.refetch()} />;

  return (
    <div className="mx-auto max-w-7xl space-y-6 pb-16">
      {/* Header Banner */}
      <div className="relative overflow-hidden rounded-3xl border border-indigo-100 bg-gradient-to-r from-slate-900 via-indigo-950 to-slate-900 p-8 text-white shadow-xl">
        <div className="flex flex-wrap items-center justify-between gap-6">
          <div className="space-y-2">
            <div className="inline-flex items-center gap-2 rounded-full bg-white/10 px-3 py-1 text-xs font-semibold text-indigo-200 backdrop-blur-md border border-white/10">
              <UserCheck className="size-3.5 text-indigo-300" /> Host Approval Center
            </div>
            <h1 className="text-3xl font-extrabold text-white sm:text-4xl">Visitor Approvals</h1>
            <p className="text-sm text-indigo-200/90 max-w-2xl">
              Review and decision visitor gate pass requests naming you as campus host.
            </p>
          </div>
        </div>
      </div>

      {/* Filter Tabs Toolbar */}
      <div className="flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-slate-200/80 bg-white p-4 shadow-sm">
        <div className="flex items-center gap-2">
          <Filter className="size-4 text-indigo-600" />
          <span className="font-bold text-slate-900 text-sm">Filter Status:</span>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {REQUEST_STATUSES.map((val) => {
            const isActive = status === val;
            return (
              <Button
                key={val}
                variant={isActive ? 'primary' : 'ghost'}
                size="sm"
                onClick={() => setFilter('status', val)}
                className={`font-semibold text-xs transition-all ${
                  isActive
                    ? 'bg-indigo-600 text-white shadow-sm'
                    : 'text-slate-600 hover:bg-slate-100'
                }`}
              >
                {val.charAt(0) + val.slice(1).toLowerCase()}
              </Button>
            );
          })}
        </div>
      </div>

      {/* Data Table */}
      <div className="overflow-hidden rounded-2xl border border-slate-200/80 bg-white shadow-sm">
        <DataTable
          columns={columns}
          data={queue.data?.items ?? []}
          loading={queue.isPending}
          {...(queue.data ? { page: queue.data } : {})}
          onPageChange={setPage}
          onRowClick={setSelected}
          mobilePrimaryColumn="visitorName"
          getRowId={(row) => String(row.id)}
          emptyHeading={
            status === 'PENDING' ? 'Nothing waiting for you' : `No ${status.toLowerCase()} requests`
          }
          emptyDescription="Requests that name you as host appear here. You are emailed when one arrives."
        />
      </div>

      <ApprovalDrawer
        request={selected}
        open={selected !== null}
        onOpenChange={(open) => {
          if (!open) setSelected(null);
        }}
      />
    </div>
  );
}
