import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { NativeSelect } from '@ui/index';
import { DataTable, PageHeader } from '@components/data';
import { ErrorState } from '@components/feedback';
import { RequestStatusBadge } from '@components/pass';
import { visitorRequestApi } from '@lib/api/services/gatepass.api';
import { requestKeys } from '@lib/query/keys';
import { formatDateTime, formatValidity } from '@lib/format/datetime';
import { REQUEST_STATUSES, type RequestStatus } from '@/types/enums';
import type { VisitorRequestResponse } from '@/types/gatepass.types';
import { useUrlPagination } from '@hooks/useUrlPagination';
import { ApprovalDrawer } from '@components/approval';

const isRequestStatus = (value: string): value is RequestStatus =>
  (REQUEST_STATUSES as readonly string[]).includes(value);

/**
 * Campus Admin screen 2 — the campus-wide visitor queue.
 *
 * `/visitor-requests` (campus) rather than `/mine` (host): a Campus Admin sees
 * every request at their campus, not only ones naming them as host.
 *
 * The approval drawer comes from components/, not from a feature — approving a
 * request is the same decision with the same server-side rules whoever makes
 * it. Phase 4 will import the same component.
 */
export default function VisitorQueuePage() {
  const { request: pageRequest, params, setPage, setFilter } = useUrlPagination(20);
  const statusParam = params.get('status') ?? 'PENDING';
  const status: RequestStatus = isRequestStatus(statusParam) ? statusParam : 'PENDING';
  const [selected, setSelected] = useState<VisitorRequestResponse | null>(null);

  const queue = useQuery({
    queryKey: requestKeys.queue(status, pageRequest),
    queryFn: () => visitorRequestApi.queue(status, pageRequest),
  });

  const columns: ColumnDef<VisitorRequestResponse, unknown>[] = [
    {
      id: 'visitorName',
      header: 'Visitor',
      accessorKey: 'visitorName',
      cell: (info) => (
        <div className="min-w-0">
          <p className="text-body-md truncate text-[var(--ink-900)]">{info.row.original.visitorName}</p>
          <p className="text-caption truncate text-[var(--ink-500)]">{info.row.original.visitorEmail}</p>
        </div>
      ),
    },
    {
      id: 'purpose',
      header: 'Purpose',
      accessorKey: 'purpose',
      cell: (info) => <span className="line-clamp-2 max-w-[32ch]">{info.row.original.purpose}</span>,
    },
    {
      id: 'visitFrom',
      header: 'Visit dates',
      accessorKey: 'visitFrom',
      cell: (info) => formatValidity(info.row.original.visitFrom, info.row.original.visitTo),
    },
    {
      id: 'createdAt',
      header: 'Waiting since',
      accessorKey: 'createdAt',
      cell: (info) => formatDateTime(info.row.original.createdAt),
    },
    { id: 'status', header: 'Status', cell: (info) => <RequestStatusBadge status={info.row.original.status} /> },
  ];

  if (queue.isError) return <ErrorState error={queue.error} onRetry={() => void queue.refetch()} />;

  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      <PageHeader
        title="Visitor queue"
        description="Every visitor request at this campus. Oldest first."
        actions={
          <NativeSelect
            aria-label="Filter by status" className="w-44" value={status}
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

      <div className="surface-card overflow-hidden">
        <DataTable
          columns={columns}
          data={queue.data?.items ?? []}
          loading={queue.isPending}
          {...(queue.data ? { page: queue.data } : {})}
          onPageChange={setPage}
          onRowClick={setSelected}
          mobilePrimaryColumn="visitorName"
          getRowId={(row) => String(row.id)}
          emptyHeading="Nothing in the queue"
          emptyDescription="Visitor requests at this campus appear here."
        />
      </div>

      <ApprovalDrawer
        request={selected}
        open={selected !== null}
        onOpenChange={(open) => { if (!open) setSelected(null); }}
      />
    </div>
  );
}
