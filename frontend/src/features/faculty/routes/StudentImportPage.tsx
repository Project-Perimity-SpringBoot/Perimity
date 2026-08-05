import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { AlertTriangle, CheckCircle2, FileSpreadsheet, ImageOff, Upload } from 'lucide-react';
import { Badge, Button, SkeletonText } from '@ui/index';
import { DataTable, PageHeader } from '@components/data';
import { ConfirmDialog, ErrorState } from '@components/feedback';
import { FileDropzone } from '@components/upload';
import { IntakeFormPanel } from '../components/IntakeFormPanel';
import { studentImportApi } from '@lib/api/services/user.api';
import { importKeys } from '@lib/query/keys';
import { UPLOAD_RULES } from '@lib/validation/patterns';
import { useToast } from '@hooks/useToast';
import type { ImportBatchResponse, ImportRowResponse } from '@/types/user.types';

/**
 * Bulk student onboarding from a Google Form responses sheet.
 *
 * ==========================================================================
 * ONE PAGE, THREE PHASES, BECAUSE IT IS ONE TASK
 * ==========================================================================
 * Upload, read the preview, confirm. Splitting these across routes would mean
 * a faculty member navigating away from the thing they are deciding about, and
 * the decision needs the rejected rows in front of them.
 *
 * ==========================================================================
 * WHY CONFIRM IS A SEPARATE, DELIBERATE ACT
 * ==========================================================================
 * Rows import as VERIFIED and verifiedBy records whoever presses that button.
 * That is a real claim about a real person, so the button says what it means
 * and the dialog spells out the consequence.
 *
 * The preview between upload and confirm is also the last chance to catch the
 * ordinary disaster - last term's sheet, the wrong campus, a renamed column -
 * before it becomes two hundred accounts.
 */
export default function StudentImportPage() {
  const toast = useToast();
  const queryClient = useQueryClient();

  const [file, setFile] = useState<File | null>(null);
  const [batchId, setBatchId] = useState<number | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [showAllRows, setShowAllRows] = useState(false);

  const batch = useQuery({
    queryKey: importKeys.batch(batchId ?? 0),
    queryFn: () => studentImportApi.getBatch(batchId as number),
    enabled: batchId != null,
    /*
     * Poll only while work is in flight. A finished batch polled every two
     * seconds is a request per viewer per two seconds forever, for an answer
     * that cannot change.
     */
    refetchInterval: (query) =>
      query.state.data?.status === 'PROCESSING' ? 2000 : false,
  });

  const outcome = showAllRows ? 'ALL' : 'REJECTED';

  const rows = useQuery({
    queryKey: importKeys.rows(batchId ?? 0, outcome, { page: 0, size: 100 }),
    queryFn: () => studentImportApi.rows(batchId as number, outcome, { page: 0, size: 100 }),
    enabled: batchId != null && batch.data != null,
  });

  const upload = useMutation({
    mutationFn: (f: File) => studentImportApi.upload(f),
    onSuccess: (result) => {
      setBatchId(result.id);
      setFile(null);
      if (result.status === 'FAILED') {
        // The request succeeded; the sheet is the problem. Say which.
        toast.error('That sheet could not be used', result.failureReason ?? undefined);
      } else {
        toast.success(`Checked ${result.totalRows} rows`);
      }
    },
    onError: (error) => toast.fromError(error, 'That file could not be uploaded.'),
  });

  const confirm = useMutation({
    mutationFn: () => studentImportApi.confirm(batchId as number),
    onSuccess: (result) => {
      void queryClient.invalidateQueries({ queryKey: importKeys.all });
      setConfirming(false);
      if (result.status === 'FAILED') {
        toast.error('The import did not finish', result.failureReason ?? undefined);
      } else {
        toast.success(
          `${result.createdCount + result.updatedCount} student(s) imported`,
          result.missingPhotoCount > 0
            ? `${result.missingPhotoCount} still need a photo before a pass can be issued.`
            : undefined,
        );
      }
    },
    onError: (error) => { setConfirming(false); toast.fromError(error, 'The import failed.'); },
  });

  const columns: ColumnDef<ImportRowResponse, unknown>[] = [
    {
      id: 'rowNumber',
      header: 'Row',
      // The spreadsheet's own row number, so "row 47" means the line they can
      // open and look at.
      accessorFn: (r) => r.rowNumber,
      cell: ({ row }) => (
        <span className="text-mono text-[var(--ink-500)]">{row.original.rowNumber}</span>
      ),
    },
    { id: 'name', header: 'Student', accessorFn: (r) => r.fullName ?? '—' },
    { id: 'email', header: 'Email', accessorFn: (r) => r.email ?? '—' },
    { id: 'rollNo', header: 'Roll number', accessorFn: (r) => r.rollNo ?? '—' },
    {
      id: 'outcome',
      header: 'Outcome',
      accessorFn: (r) => r.outcome,
      cell: ({ row }) => <Badge tone="neutral">{OUTCOME_LABEL[row.original.outcome]}</Badge>,
    },
    {
      id: 'message',
      header: 'What is wrong',
      accessorFn: (r) => r.message ?? '',
      // Free text from the server, rendered as text. Never as HTML.
      cell: ({ row }) => (
        <span className="text-[var(--ink-700)]">{row.original.message ?? '—'}</span>
      ),
    },
  ];

  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      <PageHeader
        title="Import students from a form"
        description="Upload the responses sheet from your intake form. Nothing is created until you confirm."
      />

      {/* The form itself: share it, or pull the responses straight back. */}
      <IntakeFormPanel onPulled={(b) => setBatchId(b.id)} />

      {/* ---------------------------------------------------------------
          Upload, kept as the fallback rather than the main path.

          Pull does the same thing without the round trip, but a sheet can
          come from anywhere - another campus's export, a form nobody linked,
          a file someone was emailed - and those still need a way in.
         --------------------------------------------------------------- */}
      <section className="surface-card space-y-[var(--sp-4)] p-[var(--sp-5)]">
        <div className="flex items-start gap-[var(--sp-3)]">
          <FileSpreadsheet className="size-5 shrink-0 text-[var(--ink-500)]" aria-hidden />
          <div className="min-w-0">
            <h2 className="text-label text-[var(--ink-900)]">The responses sheet</h2>
            <p className="text-caption text-[var(--ink-500)]">
              In Google Forms: Responses, then the Sheets icon, then File, Download,
              Microsoft Excel (.xlsx).
            </p>
          </div>
        </div>

        {/*
          bulkSheet, the same rule the visitor bulk upload uses. Its 5MB cap
          matches ResponseSheetParser.MAX_BYTES on the server, so a file the
          browser accepts is one the parser will also accept — a mismatch there
          means an upload that succeeds and then fails for reasons the user
          cannot see.
        */}
        <FileDropzone
          rule={UPLOAD_RULES.bulkSheet}
          file={file}
          onSelect={(f) => { setFile(f); upload.mutate(f); }}
          onClear={() => setFile(null)}
          disabled={upload.isPending || confirm.isPending}
        />
        {upload.isPending && (
          <p className="text-caption text-[var(--ink-500)]">Reading the sheet…</p>
        )}
      </section>

      {batchId != null && batch.isPending && <SkeletonText lines={4} />}
      {batch.isError && (
        <ErrorState error={batch.error} onRetry={() => void batch.refetch()} />
      )}

      {batch.data && <BatchSummary batch={batch.data} />}

      {/* ---------------------------------------------------------------
          Phase 2 — the preview. Rejected rows by default, because a list of
          197 fine rows is not what anyone opens this to read.
         --------------------------------------------------------------- */}
      {batch.data && batch.data.totalRows > 0 && (
        <section className="space-y-[var(--sp-3)]">
          <div className="flex flex-wrap items-center justify-between gap-[var(--sp-3)]">
            <h2 className="text-label text-[var(--ink-900)]">
              {showAllRows ? 'Every row' : 'Rows that need attention'}
            </h2>
            <Button variant="ghost" size="sm" onClick={() => setShowAllRows((v) => !v)}>
              {showAllRows ? 'Show only problems' : 'Show every row'}
            </Button>
          </div>

          <DataTable
            columns={columns}
            data={rows.data?.items ?? []}
            loading={rows.isPending}
            mobilePrimaryColumn="name"
            getRowId={(r) => String(r.id)}
            emptyHeading={showAllRows ? 'No rows' : 'Nothing wrong'}
            emptyDescription={
              showAllRows
                ? 'This sheet had no readable rows.'
                : 'Every row passed. Confirm to create the accounts.'
            }
          />
        </section>
      )}

      {/* ---------------------------------------------------------------
          Phase 3 — confirm. The only control here that writes anything.
         --------------------------------------------------------------- */}
      {batch.data?.confirmable && batch.data.validRows > 0 && (
        <section className="surface-card flex flex-wrap items-center gap-[var(--sp-4)] p-[var(--sp-5)]">
          <p className="text-body min-w-0 flex-1 text-[var(--ink-700)]">
            <strong className="text-[var(--ink-900)]">
              {batch.data.validRows} student{batch.data.validRows === 1 ? '' : 's'} ready
            </strong>
            {' · '}
            <span className="text-[var(--ink-500)]">
              Their details will be marked verified against your name.
            </span>
          </p>
          <Button onClick={() => setConfirming(true)} loading={confirm.isPending}>
            <Upload aria-hidden />
            {batch.data.status === 'FAILED' ? 'Carry on importing' : 'Create these accounts'}
          </Button>
        </section>
      )}

      <ConfirmDialog
        open={confirming}
        onOpenChange={(open) => { if (!open) setConfirming(false); }}
        title={`Import ${batch.data?.validRows ?? 0} student(s)?`}
        description={
          'Their accounts will be created and their details recorded as verified by you. '
          + 'Students with no usable photo will need to add one before a pass can be issued.'
        }
        confirmLabel="Import and verify"
        loading={confirm.isPending}
        onConfirm={() => confirm.mutate()}
      />
    </div>
  );
}

const OUTCOME_LABEL: Record<ImportRowResponse['outcome'], string> = {
  PENDING: 'Ready',
  CREATED: 'Created',
  UPDATED: 'Updated',
  REJECTED: 'Not imported',
};

/**
 * The counts, and the two that are easy to gloss over.
 *
 * missingPhotoCount gets its own line because those students have an account
 * and verified details and still cannot get through a gate. Folding them into
 * a success count would hide a group of people who turn up on Monday and are
 * turned away.
 */
function BatchSummary({ batch }: { batch: ImportBatchResponse }) {
  const done = batch.createdCount + batch.updatedCount;

  return (
    <section className="surface-card space-y-[var(--sp-3)] p-[var(--sp-5)]">
      <div className="flex flex-wrap items-center gap-[var(--sp-3)]">
        <Badge tone="neutral">{STATUS_LABEL[batch.status]}</Badge>
        <span className="text-caption text-[var(--ink-500)]">{batch.filename}</span>
      </div>

      {batch.failureReason && (
        <p className="text-body flex gap-[var(--sp-2)]">
          <AlertTriangle className="size-4 shrink-0 mt-[2px] text-[var(--review-fg)]" aria-hidden />
          <span>{batch.failureReason}</span>
        </p>
      )}

      <dl className="grid gap-[var(--sp-4)] sm:grid-cols-4">
        <Stat label="Rows in the sheet" value={batch.totalRows} />
        <Stat label="Ready to import" value={batch.validRows} />
        <Stat label="Not imported" value={batch.rejectedCount} />
        <Stat label="Imported" value={done} />
      </dl>

      {batch.missingPhotoCount > 0 && (
        <p className="text-body flex gap-[var(--sp-2)] rounded-[var(--r-sm)] bg-[var(--surface-sunken)] p-[var(--sp-3)]">
          <ImageOff className="size-4 shrink-0 mt-[2px] text-[var(--ink-500)]" aria-hidden />
          <span>
            <strong>{batch.missingPhotoCount}</strong> student
            {batch.missingPhotoCount === 1 ? '' : 's'} imported without a photo. They can
            sign in and add one — no pass is issued until they do.
          </span>
        </p>
      )}

      {batch.status === 'COMPLETED' && batch.rejectedCount === 0 && done > 0 && (
        <p className="text-body flex gap-[var(--sp-2)] text-[var(--ink-700)]">
          <CheckCircle2 className="size-4 shrink-0 mt-[2px]" aria-hidden />
          Every row imported.
        </p>
      )}
    </section>
  );
}

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <div>
      <dt className="text-caption text-[var(--ink-500)]">{label}</dt>
      <dd className="text-h3 text-[var(--ink-900)]">{value}</dd>
    </div>
  );
}

const STATUS_LABEL: Record<ImportBatchResponse['status'], string> = {
  VALIDATING: 'Reading',
  VALIDATED: 'Checked — not yet imported',
  PROCESSING: 'Importing…',
  COMPLETED: 'Finished',
  FAILED: 'Did not finish',
};
