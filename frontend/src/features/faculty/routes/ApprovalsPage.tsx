import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { NativeSelect } from '@ui/index';
import { DataTable, PageHeader } from '@components/data';
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

/**
 * The host's approval queue.
 *
 * Deliberately the same shape as the Campus Admin's VisitorQueuePage: same
 * rows, same drawer, same status filter, differing only in the endpoint
 * (`/mine` here, campus-wide there). They used to look like two products —
 * this one had a dark gradient banner and a row of pill buttons, that one the
 * standard header and a select — for a difference invisible to the person
 * doing the work.
 */
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
      header: 'Visitor',
      accessorKey: 'visitorName',
      cell: (info) => (
        <div className="flex min-w-0 items-center gap-[var(--sp-3)]">
          <span className="text-caption flex size-9 shrink-0 items-center justify-center rounded-[var(--r-md)] bg-[var(--brand-50)] text-[var(--brand-600)]">
            {info.row.original.visitorName?.charAt(0) ?? 'V'}
          </span>
          <div className="min-w-0">
            <p className="text-body-md truncate text-[var(--ink-900)]">
              {info.row.original.visitorName}
            </p>
            <p className="text-caption truncate text-[var(--ink-500)]">
              {info.row.original.visitorEmail}
            </p>
          </div>
        </div>
      ),
    },
    {
      id: 'purpose',
      header: 'Purpose',
      accessorKey: 'purpose',
      cell: (info) => (
        <span className="text-small line-clamp-2 max-w-[32ch] text-[var(--ink-700)]">
          {info.row.original.purpose}
        </span>
      ),
    },
    {
      id: 'visitFrom',
      header: 'Visit dates',
      accessorKey: 'visitFrom',
      cell: (info) => formatValidity(info.row.original.visitFrom, info.row.original.visitTo),
    },
    {
      id: 'createdAt',
      header: 'Requested',
      accessorKey: 'createdAt',
      cell: (info) => (
        <span className="text-small text-[var(--ink-500)]">
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
    <div className="flex flex-col gap-[var(--sp-6)]">
      <PageHeader
        breadcrumbs={[{ label: 'Faculty', to: '/faculty' }, { label: 'Visitor approvals' }]}
        title="Visitor approvals"
        description="Visitor gate pass requests naming you as campus host. Open one to approve or reject it."
        actions={
          <NativeSelect
            aria-label="Filter by status"
            className="w-44"
            value={status}
            onChange={(event) => setFilter('status', event.target.value)}
          >
            {REQUEST_STATUSES.map((value) => (
              <option key={value} value={value}>
                {value.charAt(0) + value.slice(1).toLowerCase()}
              </option>
            ))}
          </NativeSelect>
        }
      />

      <div className="surface-panel overflow-hidden">
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
