import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { AlertTriangle, ClipboardCheck, Clock } from 'lucide-react';
import { Avatar, Button } from '@ui/index';
import { DataTable, PageHeader } from '@components/data';
import { ErrorState } from '@components/feedback';
import { StudentDetailsReviewDrawer } from '@components/approval';
import { studentApi } from '@lib/api/services/user.api';
import { profileKeys } from '@lib/query/keys';
import { formatDateTime, formatWaitingFor, isWaitingTooLong } from '@lib/format/datetime';
import { useUrlPagination } from '@hooks/useUrlPagination';
import type { StudentProfileResponse } from '@/types/user.types';

/**
 * The student details review queue.
 *
 * ==========================================================================
 * WHY "REVIEW" AND NOT "VERIFY" ON EACH ROW
 * ==========================================================================
 * A Verify button here would approve details this table does not show. The row
 * carries a name, a roll number and a department; the things being verified —
 * date of birth, address, phone numbers — are deliberately not in it, because a
 * table of twenty students' contact details is a data export wearing the
 * costume of a review screen.
 *
 * So one-click verify from the list means attesting to values you have not
 * read, and the entire point of the verification record is that a named person
 * looked. Review opens the details; the Verify button lives there, one click
 * later, next to the information it refers to.
 *
 * The button also fixes the real complaint behind asking for one: the action
 * used to be an invisible click-anywhere-on-the-row, which is not an affordance
 * anybody can see.
 *
 * ==========================================================================
 * OLDEST FIRST, AND NO SORT CONTROL
 * ==========================================================================
 * The server orders by submittedAt ascending. Newest-first would bury whoever
 * has waited longest at the bottom of the last page, which is how a queue
 * becomes a backlog nobody clears.
 *
 * ==========================================================================
 * NO CAMPUS PICKER
 * ==========================================================================
 * campusId is not sent. The server takes it from the token and refuses a
 * mismatch, so a faculty member sees their own campus and no other.
 */
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
      // displayName is null until a name is filled in. Falling back to the roll
      // number keeps the row identifiable instead of blank.
      accessorFn: (row) => row.displayName ?? row.rollNo ?? `Profile ${row.id}`,
      cell: ({ row }) => {
        const name = row.original.displayName ?? row.original.rollNo ?? `Profile ${row.original.id}`;
        return (
          <span className="flex min-w-0 items-center gap-[var(--sp-3)]">
            <Avatar name={name} />
            <span className="min-w-0 truncate font-medium text-[var(--ink-900)]">{name}</span>
          </span>
        );
      },
    },
    {
      id: 'rollNo',
      header: 'Roll number',
      accessorFn: (row) => row.rollNo ?? '—',
      cell: ({ row }) => (
        <span className="text-mono text-[var(--ink-700)]">{row.original.rollNo ?? '—'}</span>
      ),
    },
    {
      id: 'department',
      header: 'Department',
      accessorFn: (row) => row.departmentName ?? '—',
      cell: ({ row }) => (
        <span className="text-[var(--ink-700)]">{row.original.departmentName ?? 'Not set'}</span>
      ),
    },
    {
      id: 'submittedAt',
      header: 'Waiting',
      accessorFn: (row) => row.submittedAt ?? '',
      /*
       * "3 days" rather than a timestamp: how long someone has been waiting is
       * the number that decides what to do next. The exact submission time is
       * on the detail view for anyone who needs it, and stays in the title
       * attribute here.
       */
      cell: ({ row }) => {
        const stale = isWaitingTooLong(row.original.submittedAt);
        return (
          <span
            className="inline-flex items-center gap-[var(--sp-2)]"
            title={formatDateTime(row.original.submittedAt)}
          >
            {/* --review-fg is the AMBER family, already used for "needs a
                second look" at the gate. Reusing it keeps one meaning for the
                colour instead of inventing a second warning palette. */}
            {stale
              ? <AlertTriangle className="size-4 shrink-0 text-[var(--review-fg)]" aria-hidden />
              : <Clock className="size-4 shrink-0 text-[var(--ink-500)]" aria-hidden />}
            <span className={stale ? 'text-[var(--ink-900)]' : 'text-[var(--ink-500)]'}>
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
            /*
             * The row is clickable too, and without stopPropagation this fires
             * the row handler as well — opening the drawer twice, which flickers
             * as it re-renders mid-animation.
             */
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
        title="Student details to check"
        description="Students who have asked you to check what they entered. Oldest first."
      />

      {/* ---------------------------------------------------------------
          A one-line summary above the table.

          "1 waiting" is the number a reviewer wants before deciding whether
          this is a two-minute job or an afternoon, and the stale count calls
          out the failure mode a queue actually has — not that it is long, but
          that somebody at the bottom has been forgotten.
         --------------------------------------------------------------- */}
      {!pending.isPending && total > 0 && (
        <section className="surface-card flex flex-wrap items-center gap-[var(--sp-3)] p-[var(--sp-4)]">
          <span className="inline-flex size-9 shrink-0 items-center justify-center rounded-[var(--r-circle)] bg-[var(--brand-50)]">
            <ClipboardCheck className="size-5 text-[var(--brand-600)]" aria-hidden />
          </span>
          <p className="text-body min-w-0 flex-1 text-[var(--ink-700)]">
            <strong className="text-[var(--ink-900)]">
              {total} {total === 1 ? 'student is' : 'students are'} waiting
            </strong>
            {waitingTooLong > 0 && (
              <span className="text-[var(--ink-500)]">
                {' · '}{waitingTooLong} for more than three days
              </span>
            )}
          </p>
        </section>
      )}

      <DataTable
        columns={columns}
        data={pending.data?.items ?? []}
        loading={pending.isPending}
        {...(pending.data ? { page: pending.data } : {})}
        onPageChange={setPage}
        mobilePrimaryColumn="name"
        getRowId={(row) => String(row.id)}
        emptyHeading="Nothing waiting"
        emptyDescription="When a student sends their details for checking, they appear here."
        onRowClick={review}
      />

      <StudentDetailsReviewDrawer
        profile={selected}
        open={open}
        onOpenChange={(next) => {
          setOpen(next);
          // Keep `selected` until the panel has finished closing. Clearing it on
          // the same tick empties the drawer before it slides away.
          if (!next) setTimeout(() => setSelected(null), 200);
        }}
      />
    </div>
  );
}
