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
 * Phase 4 screen 2 — the host's own approval queue.
 *
 * `/visitor-requests/mine` rather than the campus queue, which is the one
 * difference from the Campus Admin's otherwise identical screen: a faculty
 * member decides on requests that named them as host. The campus-wide list
 * belongs to the admin, and putting it here would show one department another
 * department's visitors.
 *
 * The drawer and the reject modal are @components/approval — screens 3 and 4 of
 * this phase, deliberately not rewritten here. Approving is the same decision
 * with the same server rules whoever makes it, and the reject reason is
 * mandatory in both places because the applicant is shown it.
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
      /* span, not div/p — DataTable renders mobilePrimaryColumn inside a <p> in
         its stacked sub-640px form. See the same note in EventsPage. */
      cell: (info) => (
        <span className="block min-w-0">
          <span className="text-body-md block truncate text-[var(--ink-900)]">
            {info.row.original.visitorName}
          </span>
          <span className="text-caption block truncate text-[var(--ink-500)]">
            {info.row.original.visitorEmail}
          </span>
        </span>
      ),
    },
    {
      id: 'purpose',
      header: 'Purpose',
      accessorKey: 'purpose',
      cell: (info) => (
        <span className="line-clamp-2 max-w-[32ch]">{info.row.original.purpose}</span>
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
      header: 'Waiting since',
      accessorKey: 'createdAt',
      cell: (info) => formatDateTime(info.row.original.createdAt),
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
        title="Approvals"
        description="Visitor requests naming you as host. Oldest first."
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
          emptyHeading={
            status === 'PENDING' ? 'Nothing waiting for you' : `No ${status.toLowerCase()} requests`
          }
          emptyDescription="Requests that name you as host appear here. You are emailed when one arrives."
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
