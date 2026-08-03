import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import {
  Button, Dialog, DialogBody, DialogContent, DialogHeader, DialogTitle, NativeSelect,
} from '@ui/index';
import { DataTable, DescriptionList, PageHeader } from '@components/data';
import { ErrorState } from '@components/feedback';
import { authApi } from '@lib/api/services/auth.api';
import { authKeys } from '@lib/query/keys';
import { formatDateTime } from '@lib/format/datetime';
import type { AuditLogResponse } from '@/types/auth.types';
import { ROLE_LABEL } from '@/layouts/navigation';
import { useUrlPagination } from '@hooks/useUrlPagination';

/**
 * Batch 5 screens 10 and 11 — the audit log.
 *
 * APPEND-ONLY. There is no edit and no delete on this screen because a log
 * somebody can tidy is not evidence.
 *
 * The mockup shows a before/after diff. `AuditLogResponse.details` is free text
 * — there is no structured diff anywhere in the backend — so the expanded row
 * renders the detail the server actually recorded, and the diff is formally
 * descoped rather than fabricated from a string.
 */
const ACTIONS = [
  'LOGIN_SUCCESS', 'LOGIN_FAILED', 'LOGOUT', 'ACCOUNT_LOCKED',
  'OTP_REQUESTED', 'OTP_FAILED', 'PASSWORD_CHANGED', 'PASSWORD_RESET_REQUESTED',
  'ACCOUNT_CREATED', 'ACCOUNT_DEACTIVATED',
  'REQUEST_APPROVED', 'REQUEST_REJECTED', 'PASS_REVOKED',
  'BLOCKLIST_ADDED', 'BLOCKLIST_REMOVED', 'BLOCKED_REGISTRATION_ATTEMPT',
  'CAMPUS_CONFIG_CHANGED', 'SHIFT_STARTED', 'SHIFT_ENDED',
  'BULK_BLOCKLIST_SCREENED', 'BULK_IDENTITY_RESOLVED',
] as const;

const humanise = (action: string): string =>
  action.charAt(0) + action.slice(1).toLowerCase().replace(/_/g, ' ');

export default function AuditLogPage({ scope = 'campus' }: { scope?: 'campus' | 'platform' }) {
  const { request: pageRequest, params, setPage, setFilter } = useUrlPagination(50);
  const action = params.get('action') ?? '';
  const [selected, setSelected] = useState<AuditLogResponse | null>(null);

  const listQuery = { ...pageRequest, ...(action ? { action } : {}) };

  const entries = useQuery({
    queryKey: authKeys.auditList(listQuery),
    queryFn: () => authApi.listAudit(listQuery),
  });

  const columns: ColumnDef<AuditLogResponse, unknown>[] = [
    {
      id: 'createdAt',
      header: 'When',
      accessorKey: 'createdAt',
      cell: (info) => formatDateTime(info.row.original.createdAt),
    },
    { id: 'action', header: 'Action', cell: (info) => humanise(info.row.original.action) },
    {
      id: 'actor',
      header: 'Actor',
      cell: (info) => {
        const row = info.row.original;
        if (row.actorUserId === null) return 'System';
        return `${row.actorRole ? ROLE_LABEL[row.actorRole] : 'User'} #${row.actorUserId}`;
      },
    },
    {
      id: 'targetEntity',
      header: 'Target',
      cell: (info) => (
        <span className="text-mono">{info.row.original.targetEntity ?? '—'}</span>
      ),
    },
    {
      id: 'sourceIp',
      header: 'Source',
      cell: (info) => <span className="text-mono">{info.row.original.sourceIp ?? '—'}</span>,
    },
  ];

  if (entries.isError) return <ErrorState error={entries.error} onRetry={() => void entries.refetch()} />;

  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      <PageHeader
        title={scope === 'platform' ? 'Platform audit' : 'Audit log'}
        description="Every approval, rejection, configuration change and sign-in. Append-only."
        actions={
          <NativeSelect
            aria-label="Filter by action" className="w-56" value={action}
            onChange={(event) => setFilter('action', event.target.value || null)}
          >
            <option value="">All actions</option>
            {ACTIONS.map((value) => (
              <option key={value} value={value}>{humanise(value)}</option>
            ))}
          </NativeSelect>
        }
      />

      <div className="surface-card overflow-hidden">
        <DataTable
          columns={columns}
          data={entries.data?.items ?? []}
          loading={entries.isPending}
          {...(entries.data ? { page: entries.data } : {})}
          onPageChange={setPage}
          onRowClick={setSelected}
          mobilePrimaryColumn="createdAt"
          getRowId={(row) => String(row.id)}
          emptyHeading="No audit entries"
          emptyDescription="Recorded actions appear here as they happen."
        />
      </div>

      <Dialog open={selected !== null} onOpenChange={(open) => { if (!open) setSelected(null); }}>
        <DialogContent side="right">
          <DialogHeader>
            <DialogTitle>{selected ? humanise(selected.action) : ''}</DialogTitle>
          </DialogHeader>
          <DialogBody>
            {selected ? (
              <DescriptionList
                columns={1}
                items={[
                  { label: 'When', value: formatDateTime(selected.createdAt) },
                  {
                    label: 'Actor',
                    value:
                      selected.actorUserId === null
                        ? 'System'
                        : `${selected.actorRole ? ROLE_LABEL[selected.actorRole] : 'User'} #${selected.actorUserId}`,
                  },
                  { label: 'Target', value: selected.targetEntity },
                  { label: 'Source IP', value: selected.sourceIp },
                  { label: 'Detail', value: selected.details },
                ]}
              />
            ) : null}
            <p className="text-caption mt-[var(--sp-6)] text-[var(--ink-500)]">
              The detail above is recorded as free text. This backend stores no
              before/after values, so there is no field-by-field diff to show.
            </p>
          </DialogBody>
        </DialogContent>
      </Dialog>

      {entries.data && entries.data.total > 0 ? (
        <div className="flex justify-end">
          <Button variant="ghost" size="sm" onClick={() => setFilter('action', null)}>
            Clear filter
          </Button>
        </div>
      ) : null}
    </div>
  );
}
