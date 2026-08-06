import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import {
  AlertTriangle,
  CheckCircle2,
  FileSpreadsheet,
  ImageOff,
  Upload,
  UserCheck,
  Users,
  AlertCircle,
  Clock,
  Sparkles,
  ShieldCheck,
  Filter,
} from 'lucide-react';
import { Badge, Button, SkeletonText } from '@ui/index';
import { DataTable } from '@components/data';
import { ConfirmDialog, ErrorState } from '@components/feedback';
import { FileDropzone } from '@components/upload';
import { IntakeFormPanel } from '../components/IntakeFormPanel';
import { studentImportApi } from '@lib/api/services/user.api';
import { importKeys } from '@lib/query/keys';
import { UPLOAD_RULES } from '@lib/validation/patterns';
import { useToast } from '@hooks/useToast';
import type { ImportBatchResponse, ImportRowResponse } from '@/types/user.types';

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
      void queryClient.invalidateQueries({ queryKey: importKeys.all });
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
      cell: ({ row }) => (
        <span className="inline-flex size-6 items-center justify-center rounded-md bg-slate-100 font-mono text-xs font-semibold text-slate-600">
          #{row.original.rowNumber}
        </span>
      ),
    },
    {
      id: 'name',
      header: 'Student Name',
      accessorFn: (r) => r.fullName ?? '—',
      cell: ({ row }) => (
        <div className="font-semibold text-slate-900">{row.original.fullName ?? '—'}</div>
      ),
    },
    {
      id: 'email',
      header: 'Email',
      accessorFn: (r) => r.email ?? '—',
      cell: ({ row }) => (
        <span className="font-mono text-xs text-slate-600">{row.original.email ?? '—'}</span>
      ),
    },
    {
      id: 'rollNo',
      header: 'Roll Number',
      accessorFn: (r) => r.rollNo ?? '—',
      cell: ({ row }) => (
        <span className="inline-flex items-center rounded-md bg-indigo-50 px-2 py-0.5 font-mono text-xs font-semibold text-indigo-700">
          {row.original.rollNo ?? '—'}
        </span>
      ),
    },
    {
      id: 'outcome',
      header: 'Status',
      accessorFn: (r) => r.outcome,
      cell: ({ row }) => {
        const val = row.original.outcome;
        let badgeTone: 'neutral' | 'brand' = 'neutral';
        if (val === 'PENDING' || val === 'CREATED' || val === 'UPDATED') badgeTone = 'brand';

        return <Badge tone={badgeTone}>{OUTCOME_LABEL[val]}</Badge>;
      },
    },
    {
      id: 'message',
      header: 'Validation Notes',
      accessorFn: (r) => r.message ?? '',
      cell: ({ row }) => (
        <span className="text-xs text-slate-600">{row.original.message ?? '—'}</span>
      ),
    },
  ];

  return (
    <div className="mx-auto max-w-7xl space-y-8 pb-20">
      {/* Hero Header */}
      <div className="relative overflow-hidden rounded-3xl border border-indigo-100 bg-gradient-to-r from-indigo-900 via-indigo-800 to-slate-900 p-8 text-white shadow-xl">
        <div className="relative z-10 flex flex-wrap items-center justify-between gap-6">
          <div className="max-w-2xl space-y-2">
            <div className="inline-flex items-center gap-2 rounded-full bg-white/10 px-3 py-1 text-xs font-semibold text-indigo-200 backdrop-blur-md border border-white/10">
              <Sparkles className="size-3.5 text-indigo-300" />
              <span>Bulk Student Onboarding & Gate Pass Pipeline</span>
            </div>
            <h1 className="text-3xl font-extrabold tracking-tight text-white sm:text-4xl">
              Import Students from Form
            </h1>
            <p className="text-sm leading-relaxed text-indigo-200/90">
              Upload your Google Form intake sheet to generate verified student profiles, trigger account credentials, and dispatch gate passes automatically.
            </p>
          </div>

          <div className="flex items-center gap-3 rounded-2xl bg-white/10 p-4 backdrop-blur-md border border-white/10">
            <div className="flex size-12 items-center justify-center rounded-xl bg-white/20 text-white">
              <UserCheck className="size-6" />
            </div>
            <div>
              <div className="text-xs font-semibold uppercase tracking-wider text-indigo-200">Verification Model</div>
              <div className="text-sm font-bold text-white">Faculty Attested Intake</div>
            </div>
          </div>
        </div>
      </div>

      {/* Step 1: Form Setup */}
      <IntakeFormPanel onPulled={(b) => setBatchId(b.id)} />

      {/* Step 2: Upload responses sheet */}
      <section className="relative overflow-hidden rounded-2xl border border-slate-200/80 bg-white p-6 shadow-sm sm:p-8">
        <div className="flex items-center gap-3 border-b border-slate-100 pb-5">
          <div className="flex size-10 items-center justify-center rounded-xl bg-indigo-50 text-indigo-600 border border-indigo-100">
            <FileSpreadsheet className="size-5" />
          </div>
          <div>
            <h2 className="font-bold text-slate-900 text-lg">Upload Responses Spreadsheet</h2>
            <p className="text-xs text-slate-500">
              Export your Google Form responses as Microsoft Excel (<code className="font-mono text-indigo-600">.xlsx</code>) and drop it here.
            </p>
          </div>
        </div>

        <div className="mt-6">
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
            <div className="mt-3 flex items-center justify-center gap-2 text-xs font-medium text-indigo-600">
              <Clock className="size-4 animate-spin" />
              Parsing & validating sheet structure...
            </div>
          )}
        </div>
      </section>

      {/* Loading & Error states */}
      {batchId != null && batch.isPending && (
        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <SkeletonText lines={4} />
        </div>
      )}

      {batch.isError && (
        <ErrorState error={batch.error} onRetry={() => void batch.refetch()} />
      )}

      {/* Step 3: Batch Summary Dashboard */}
      {batch.data && <BatchSummary batch={batch.data} />}

      {/* Step 4: Preview Data Table */}
      {batch.data && batch.data.totalRows > 0 && (
        <section className="space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-slate-200/80 bg-white p-4 shadow-sm">
            <div className="flex items-center gap-2.5">
              <Filter className="size-4 text-indigo-600" />
              <h2 className="font-bold text-slate-900 text-base">
                {showAllRows ? 'All Sheet Rows' : 'Rows Needing Attention'}
              </h2>
              <span className="rounded-full bg-slate-100 px-2.5 py-0.5 font-mono text-xs font-semibold text-slate-700">
                {rows.data?.items?.length ?? 0}
              </span>
            </div>

            <Button
              variant="secondary"
              size="sm"
              onClick={() => setShowAllRows((v) => !v)}
              className="gap-2"
            >
              {showAllRows ? 'Show Only Problems' : 'Show All Rows'}
            </Button>
          </div>

          <div className="overflow-hidden rounded-2xl border border-slate-200/80 bg-white shadow-sm">
            <DataTable
              columns={columns}
              data={rows.data?.items ?? []}
              loading={rows.isPending}
              mobilePrimaryColumn="name"
              getRowId={(r) => String(r.id)}
              emptyHeading={showAllRows ? 'No rows found' : 'No Validation Problems'}
              emptyDescription={
                showAllRows
                  ? 'This sheet had no readable rows.'
                  : 'All rows passed verification checks cleanly! Ready to create accounts.'
              }
            />
          </div>
        </section>
      )}

      {/* Floating Action Confirmation Bar */}
      {batch.data?.confirmable && batch.data.validRows > 0 && (
        <div className="sticky bottom-6 z-30 flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-indigo-200/80 bg-white/95 p-5 shadow-2xl backdrop-blur-md">
          <div className="flex items-center gap-3">
            <div className="flex size-10 items-center justify-center rounded-xl bg-emerald-500 text-white shadow-md shadow-emerald-500/20">
              <ShieldCheck className="size-6" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="font-extrabold text-slate-900 text-base">
                  {batch.data.validRows} Student{batch.data.validRows === 1 ? '' : 's'} Ready
                </span>
                <span className="rounded-full bg-emerald-100 px-2 py-0.5 font-semibold text-xs text-emerald-800">
                  PASSED VERIFICATION
                </span>
              </div>
              <p className="text-xs text-slate-500">
                Details will be marked verified and standing Daily Passes issued automatically.
              </p>
            </div>
          </div>

          <Button
            size="lg"
            onClick={() => setConfirming(true)}
            loading={confirm.isPending}
            className="gap-2 bg-indigo-600 px-6 font-semibold text-white shadow-lg shadow-indigo-500/25 hover:bg-indigo-700"
          >
            <Upload className="size-4" />
            {batch.data.status === 'FAILED' ? 'Resume Importing' : 'Create Accounts & Issue Passes'}
          </Button>
        </div>
      )}

      <ConfirmDialog
        open={confirming}
        onOpenChange={(open) => {
          if (!open) setConfirming(false);
        }}
        title={`Confirm Import for ${batch.data?.validRows ?? 0} Student(s)?`}
        description={
          'Accounts will be created, profiles verified, and standing daily passes issued. ' +
          'Credentials & PDF passes will be emailed to each student.'
        }
        confirmLabel="Confirm & Issue Passes"
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
    <section className="relative overflow-hidden rounded-2xl border border-slate-200/80 bg-white p-6 shadow-sm space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 pb-4">
        <div className="flex items-center gap-3">
          <Badge tone="neutral">{STATUS_LABEL[batch.status]}</Badge>
          <span className="font-mono text-xs font-semibold text-slate-600">{batch.filename}</span>
        </div>
        <span className="text-xs text-slate-400">Batch #{batch.id}</span>
      </div>

      {batch.failureReason && (
        <div className="flex items-start gap-3 rounded-xl bg-amber-50 p-4 text-xs text-amber-900 border border-amber-200">
          <AlertTriangle className="size-4 shrink-0 text-amber-600 mt-0.5" aria-hidden />
          <span>{batch.failureReason}</span>
        </div>
      )}

      <dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard
          label="Total Sheet Rows"
          value={batch.totalRows}
          icon={Users}
          color="bg-slate-50 text-slate-700 border-slate-200"
        />
        <StatCard
          label="Ready to Import"
          value={batch.validRows}
          icon={CheckCircle2}
          color="bg-emerald-50 text-emerald-700 border-emerald-200"
        />
        <StatCard
          label="Rejected / Problems"
          value={batch.rejectedCount}
          icon={AlertCircle}
          color="bg-rose-50 text-rose-700 border-rose-200"
        />
        <StatCard
          label="Imported & Passes Issued"
          value={done}
          icon={ShieldCheck}
          color="bg-indigo-50 text-indigo-700 border-indigo-200"
        />
      </dl>

      {batch.missingPhotoCount > 0 && (
        <div className="flex items-center gap-3 rounded-xl bg-amber-50/80 p-4 text-xs text-amber-900 border border-amber-200/80">
          <ImageOff className="size-4 text-amber-600 shrink-0" aria-hidden />
          <span>
            <strong>{batch.missingPhotoCount}</strong> student{batch.missingPhotoCount === 1 ? '' : 's'} imported without a photo.
            They can sign in and add one before their gate pass activates.
          </span>
        </div>
      )}

      {batch.status === 'COMPLETED' && batch.rejectedCount === 0 && done > 0 && (
        <div className="flex items-center gap-2.5 rounded-xl bg-emerald-50 p-4 text-xs font-semibold text-emerald-800 border border-emerald-200">
          <CheckCircle2 className="size-4 text-emerald-600 shrink-0" aria-hidden />
          All rows imported successfully and gate passes dispatched!
        </div>
      )}
    </section>
  );
}

function StatCard({
  label,
  value,
  icon: Icon,
  color,
}: {
  label: string;
  value: number;
  icon: React.ComponentType<{ className?: string }>;
  color: string;
}) {
  return (
    <div className="flex items-center justify-between rounded-xl border p-4 shadow-2xs bg-white">
      <div>
        <dt className="text-xs font-medium text-slate-500">{label}</dt>
        <dd className="mt-1 text-2xl font-extrabold text-slate-900">{value}</dd>
      </div>
      <div className={`flex size-10 items-center justify-center rounded-xl border ${color}`}>
        <Icon className="size-5" />
      </div>
    </div>
  );
}

const STATUS_LABEL: Record<ImportBatchResponse['status'], string> = {
  VALIDATING: 'Reading Sheet',
  VALIDATED: 'Checked — Ready for Confirm',
  PROCESSING: 'Importing & Issuing Passes…',
  COMPLETED: 'Completed Successfully',
  FAILED: 'Import Failed / Incomplete',
};
