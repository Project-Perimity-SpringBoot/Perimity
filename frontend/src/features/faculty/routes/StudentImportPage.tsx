import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import {
  AlertCircle,
  CheckCircle2,
  Clock,
  FileSpreadsheet,
  Filter,
  ImageOff,
  ShieldCheck,
  Upload,
  Users,
} from 'lucide-react';
import { Badge, Button, SkeletonText } from '@ui/index';
import { DataTable, PageHeader, SectionHeader, StatCard, Stepper } from '@components/data';
import { Alert, ConfirmDialog, ErrorState } from '@components/feedback';
import { FileDropzone } from '@components/upload';
import { IntakeFormPanel } from '../components/IntakeFormPanel';
import { studentImportApi } from '@lib/api/services/user.api';
import { authKeys, importKeys, passKeys, profileKeys } from '@lib/query/keys';
import { UPLOAD_RULES } from '@lib/validation/patterns';
import { useToast } from '@hooks/useToast';
import type { ImportBatchResponse, ImportRowResponse } from '@/types/user.types';

const STEPS = ['Form setup', 'Upload responses', 'Review & confirm'];

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
        toast.error('That sheet could not be used', result.failureReason ?? undefined);
      } else {
        toast.success(`Successfully parsed ${result.totalRows} rows`);
      }
    },
    onError: (error) => toast.fromError(error, 'That file could not be uploaded.'),
  });

  const confirm = useMutation({
    mutationFn: () => studentImportApi.confirm(batchId as number),
    onSuccess: (result) => {
      /*
       * ====================================================================
       *  A CONFIRM CHANGES FAR MORE THAN THE BATCH
       * ====================================================================
       * This used to invalidate importKeys alone, which is only the batch and
       * its rows - the one screen the user is already looking at. Everything
       * else the confirm actually changed stayed on cached data:
       *
       *   profileKeys  new student profiles, and the pending-verification
       *                queue, since imported rows arrive already VERIFIED
       *   authKeys     the accounts auth-service just created
       *   passKeys     a standing DAILY pass per imported student, which is
       *                what the admin overview's ACTIVE count reads
       *
       * So a faculty member imported thirty students and every count in the
       * app carried on showing the number from before. Nothing was wrong in
       * the database; the screens simply never asked again.
       *
       * Invalidating the ROOT key of each, not a specific one. A confirm can
       * create, update and reject rows in the same run, and enumerating every
       * affected key here means this list silently goes stale the next time
       * someone adds a count somewhere.
       */
      void queryClient.invalidateQueries({ queryKey: importKeys.all });
      void queryClient.invalidateQueries({ queryKey: profileKeys.all });
      void queryClient.invalidateQueries({ queryKey: authKeys.all });
      void queryClient.invalidateQueries({ queryKey: passKeys.all });
      setConfirming(false);
      if (result.status === 'FAILED') {
        toast.error('The import did not finish', result.failureReason ?? undefined);
      } else {
        toast.success(
          `${result.createdCount + result.updatedCount} student(s) imported & passes issued!`,
          result.missingPhotoCount > 0
            ? `${result.missingPhotoCount} still need a photo before a pass can be issued.`
            : 'Passes dispatched to MailHog.'
        );
      }
    },
    onError: (error) => {
      setConfirming(false);
      toast.fromError(error, 'The import failed.');
    },
  });

  const columns: ColumnDef<ImportRowResponse, unknown>[] = [
    {
      id: 'rowNumber',
      header: 'Row',
      accessorFn: (r) => r.rowNumber,
      cell: ({ row }) => <span className="text-mono text-[var(--ink-500)]">#{row.original.rowNumber}</span>,
    },
    {
      id: 'name',
      header: 'Student name',
      accessorFn: (r) => r.fullName ?? '—',
      cell: ({ row }) => (
        <span className="text-body-md text-[var(--ink-900)]">{row.original.fullName ?? '—'}</span>
      ),
    },
    {
      id: 'email',
      header: 'Email',
      accessorFn: (r) => r.email ?? '—',
      cell: ({ row }) => <span className="text-mono">{row.original.email ?? '—'}</span>,
    },
    {
      id: 'rollNo',
      header: 'Roll number',
      accessorFn: (r) => r.rollNo ?? '—',
      cell: ({ row }) => <span className="text-mono">{row.original.rollNo ?? '—'}</span>,
    },
    {
      id: 'outcome',
      header: 'Status',
      accessorFn: (r) => r.outcome,
      cell: ({ row }) => {
        const val = row.original.outcome;
        const tone = val === 'PENDING' || val === 'CREATED' || val === 'UPDATED' ? 'brand' : 'neutral';
        return <Badge tone={tone}>{OUTCOME_LABEL[val]}</Badge>;
      },
    },
    {
      id: 'message',
      header: 'Validation notes',
      accessorFn: (r) => r.message ?? '',
      cell: ({ row }) => (
        <span className="text-small text-[var(--ink-500)]">{row.original.message ?? '—'}</span>
      ),
    },
  ];

  /* Which step the user is actually on, derived rather than tracked: state
     that can disagree with the data is state that eventually will. */
  const currentStep = batch.data ? 2 : 1;

  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      <PageHeader
        breadcrumbs={[{ label: 'Faculty', to: '/faculty' }, { label: 'Import students' }]}
        title="Import students from a form"
        description="Upload your intake form responses to create verified student profiles, send account credentials, and issue gate passes."
      />

      <div className="surface-panel px-[var(--sp-6)] py-[var(--sp-6)]">
        <Stepper steps={STEPS} current={currentStep} />
      </div>

      <IntakeFormPanel onPulled={(b) => setBatchId(b.id)} />

      <section className="surface-panel flex flex-col gap-[var(--sp-4)] p-[var(--sp-6)]">
        <SectionHeader
          icon={FileSpreadsheet}
          title="Upload responses spreadsheet"
          description="Export your form responses as Excel (.xlsx) and drop the file here."
          divided
        />

        <FileDropzone
          rule={UPLOAD_RULES.bulkSheet}
          file={file}
          onSelect={(f) => {
            setFile(f);
            upload.mutate(f);
          }}
          onClear={() => setFile(null)}
          disabled={upload.isPending || confirm.isPending}
        />

        {upload.isPending && (
          <p className="text-small flex items-center justify-center gap-[var(--sp-2)] text-[var(--brand-600)]">
            <Clock className="size-4 animate-spin" aria-hidden />
            Parsing and validating the sheet…
          </p>
        )}
      </section>

      {batchId != null && batch.isPending && (
        <div className="surface-panel p-[var(--sp-6)]">
          <SkeletonText lines={4} />
        </div>
      )}

      {batch.isError && <ErrorState error={batch.error} onRetry={() => void batch.refetch()} />}

      {batch.data && <BatchSummary batch={batch.data} />}

      {batch.data && batch.data.totalRows > 0 && (
        <section className="flex flex-col gap-[var(--sp-4)]">
          <SectionHeader
            icon={Filter}
            title={showAllRows ? 'All sheet rows' : 'Rows needing attention'}
            badge={<Badge>{rows.data?.items?.length ?? 0}</Badge>}
            divided
            actions={
              <Button variant="secondary" size="sm" onClick={() => setShowAllRows((v) => !v)}>
                {showAllRows ? 'Show only problems' : 'Show all rows'}
              </Button>
            }
          />

          <div className="surface-panel overflow-hidden">
            <DataTable
              columns={columns}
              data={rows.data?.items ?? []}
              loading={rows.isPending}
              mobilePrimaryColumn="name"
              getRowId={(r) => String(r.id)}
              emptyHeading={showAllRows ? 'No rows found' : 'No validation problems'}
              emptyDescription={
                showAllRows
                  ? 'This sheet had no readable rows.'
                  : 'Every row passed its checks — ready to create accounts.'
              }
            />
          </div>
        </section>
      )}

      {/* The confirm bar sticks to the bottom of the viewport because the row
          table above it can run to a hundred rows: the action that ends the
          flow must not be something you have to scroll back up to find. */}
      {batch.data?.confirmable && batch.data.validRows > 0 && (
        <div className="surface-panel sticky bottom-[var(--sp-4)] z-30 flex flex-wrap items-center justify-between gap-[var(--sp-4)] p-[var(--sp-4)] shadow-[var(--sh-overlay)]">
          <div className="flex min-w-0 items-center gap-[var(--sp-3)]">
            <span className="flex size-10 shrink-0 items-center justify-center rounded-[var(--r-md)] bg-[var(--brand-50)]">
              <ShieldCheck className="size-5 text-[var(--brand-600)]" aria-hidden />
            </span>
            <div className="min-w-0">
              <p className="text-body-md text-[var(--ink-900)]">
                {batch.data.validRows} student{batch.data.validRows === 1 ? '' : 's'} ready
              </p>
              <p className="text-small text-[var(--ink-500)]">
                Details are marked verified and standing daily passes issued automatically.
              </p>
            </div>
          </div>

          <Button size="lg" onClick={() => setConfirming(true)} loading={confirm.isPending}>
            <Upload aria-hidden />
            {batch.data.status === 'FAILED' ? 'Resume importing' : 'Create accounts & issue passes'}
          </Button>
        </div>
      )}

      <ConfirmDialog
        open={confirming}
        onOpenChange={(open) => {
          if (!open) setConfirming(false);
        }}
        title={`Confirm import for ${batch.data?.validRows ?? 0} student(s)?`}
        description={
          'Accounts will be created, profiles verified, and standing daily passes issued. ' +
          'Credentials and PDF passes are emailed to each student.'
        }
        confirmLabel="Confirm & issue passes"
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
  REJECTED: 'Rejected',
};

function BatchSummary({ batch }: { batch: ImportBatchResponse }) {
  const done = batch.createdCount + batch.updatedCount;

  return (
    <section className="flex flex-col gap-[var(--sp-4)]">
      <SectionHeader
        title="Batch summary"
        description={`${batch.filename} · batch #${batch.id}`}
        badge={<Badge>{STATUS_LABEL[batch.status]}</Badge>}
        divided
      />

      {batch.failureReason && (
        <Alert tone="warning" title="This batch reported a problem">
          {batch.failureReason}
        </Alert>
      )}

      <div className="grid gap-[var(--sp-4)] sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label="Total sheet rows" value={batch.totalRows} icon={Users} />
        <StatCard label="Ready to import" value={batch.validRows} icon={CheckCircle2} />
        <StatCard label="Rejected" value={batch.rejectedCount} icon={AlertCircle} />
        <StatCard label="Imported" value={done} icon={ShieldCheck} />
      </div>

      {batch.missingPhotoCount > 0 && (
        <Alert tone="warning" icon={ImageOff} live={false} title={`${batch.missingPhotoCount} imported without a photo`}>
          They can sign in and add one before their gate pass activates.
        </Alert>
      )}

      {batch.status === 'COMPLETED' && batch.rejectedCount === 0 && done > 0 && (
        <Alert tone="success" live={false}>
          All rows imported successfully and gate passes dispatched.
        </Alert>
      )}
    </section>
  );
}

const STATUS_LABEL: Record<ImportBatchResponse['status'], string> = {
  VALIDATING: 'Reading sheet',
  VALIDATED: 'Ready to confirm',
  PROCESSING: 'Importing & issuing passes',
  COMPLETED: 'Completed',
  FAILED: 'Failed / incomplete',
};
