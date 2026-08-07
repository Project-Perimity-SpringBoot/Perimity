import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { ArrowRight, FileSpreadsheet, ImageOff } from 'lucide-react';
import { Badge, Button, SkeletonText } from '@ui/index';
import { SectionHeader } from '@components/data';
import { EmptyState } from '@components/feedback';
import { studentImportApi } from '@lib/api/services/user.api';
import { importKeys } from '@lib/query/keys';
import { formatDateTime } from '@lib/format/datetime';
import type { ImportBatchResponse } from '@/types/user.types';

/**
 * The student cohorts recently brought in through the intake form.
 *
 * ==========================================================================
 * NOT THE SAME THING AS "ACTIVE BATCHES"
 * ==========================================================================
 * The overview already has a panel called Active batches. That one is the
 * VISITOR bulk engine - a different feature that happens to share the word
 * "batch", and it hides itself when nothing is running. A faculty member who
 * has just imported students and sees an unrelated, often empty batch list is
 * entitled to conclude the import did nothing.
 *
 * ==========================================================================
 * WHY BATCHES AND NOT STUDENTS
 * ==========================================================================
 * The obvious build was "students imported recently", one row each. It is the
 * wrong shape: an import either worked for everyone or it has a handful of
 * rows that need a person, and thirty green rows tell nobody anything. The
 * batch is the unit a faculty member acts on - it carries the rejections, the
 * missing photos, and the button that resumes a failed run.
 *
 * Imported students do not appear in the verification queue, by design: the
 * import marks them VERIFIED because a member of staff supplied the details.
 * This panel is the confirmation that used to be missing.
 */
export function RecentImportsPanel() {
  const batches = useQuery({
    queryKey: importKeys.list({ page: 0, size: 5 }),
    queryFn: () => studentImportApi.list({ page: 0, size: 5 }),
  });

  const rows = batches.data?.items ?? [];

  return (
    <section aria-labelledby="recent-imports" className="flex flex-col gap-[var(--sp-4)]">
      <SectionHeader
        id="recent-imports"
        icon={FileSpreadsheet}
        title="Recent student imports"
        description="Cohorts from the intake form. Accounts created, details verified, passes issued."
        divided
        actions={
          <Button variant="ghost" size="sm" asChild>
            <Link to="/faculty/students/import">
              Import students <ArrowRight aria-hidden />
            </Link>
          </Button>
        }
      />

      {batches.isPending ? (
        <div className="surface-card p-[var(--sp-6)]">
          <SkeletonText lines={3} />
        </div>
      ) : rows.length === 0 ? (
        <EmptyState
          icon={FileSpreadsheet}
          heading="No imports yet"
          description="Share your intake form link with students, then upload their responses here to create accounts in bulk."
        />
      ) : (
        <div className="grid gap-[var(--sp-3)]">
          {rows.map((batch) => (
            <BatchRow key={batch.id} batch={batch} />
          ))}
        </div>
      )}
    </section>
  );
}

function BatchRow({ batch }: { batch: ImportBatchResponse }) {
  const imported = batch.createdCount + batch.updatedCount;

  return (
    <Link
      to="/faculty/students/import"
      className="surface-card flex flex-wrap items-center gap-[var(--sp-4)] p-[var(--sp-4)] transition-shadow hover:shadow-md"
    >
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-[var(--sp-2)]">
          <span className="truncate font-medium">{batch.filename ?? `Batch ${batch.id}`}</span>
          <StatusBadge status={batch.status} confirmable={batch.confirmable} />
        </div>
        <p className="text-caption text-[var(--fg-muted)]">
          {formatDateTime(batch.finishedAt ?? batch.createdAt)}
          {' · '}
          {batch.totalRows} row{batch.totalRows === 1 ? '' : 's'} in the sheet
        </p>
      </div>

      <div className="flex items-center gap-[var(--sp-4)] text-sm">
        <Figure value={imported} label="imported" />
        {batch.rejectedCount > 0 && <Figure value={batch.rejectedCount} label="rejected" />}

        {/*
          Shown only when non-zero. A permanent "0 without a photo" column
          trains people to stop reading the row, and this is the number that
          decides whether a student can be given a pass.
        */}
        {batch.missingPhotoCount > 0 && (
          <span className="inline-flex items-center gap-[var(--sp-1)] font-medium">
            <ImageOff className="size-4" aria-hidden />
            {batch.missingPhotoCount}
            <span className="text-[var(--fg-muted)]">no photo</span>
          </span>
        )}
      </div>

      <ArrowRight className="size-4 shrink-0 text-[var(--fg-muted)]" aria-hidden />
    </Link>
  );
}

function Figure({ value, label }: { value: number; label: string }) {
  return (
    <span className="font-medium">
      {value} <span className="font-normal text-[var(--fg-muted)]">{label}</span>
    </span>
  );
}

function StatusBadge({ status, confirmable }: { status: string; confirmable: boolean }) {
  /*
   * FAILED is deliberately not called failed. A failed batch is resumable -
   * confirming again picks up only the rows that were never written - and
   * "Failed" reads as terminal, which is what stopped people retrying it.
   */
  if (status === 'FAILED') {
    return <Badge tone="brand">Needs another run</Badge>;
  }
  if (confirmable) {
    return <Badge tone="brand">Waiting for you to confirm</Badge>;
  }
  if (status === 'COMPLETED') {
    return <Badge>Done</Badge>;
  }
  return <Badge>{status}</Badge>;
}
