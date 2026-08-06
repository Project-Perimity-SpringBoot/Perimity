import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import { Download, FileSpreadsheet, Sparkles } from 'lucide-react';
import { Button, Field, NativeSelect, Progress } from '@ui/index';
import { FormError } from '@components/feedback';
import { FileDropzone } from '@components/upload';
import { bulkApi, eventApi } from '@lib/api/services/gatepass.api';
import { eventKeys } from '@lib/query/keys';
import { saveFile } from '@lib/api/download';
import { UPLOAD_RULES, BULK_COLUMNS } from '@lib/validation/patterns';
import { useToast } from '@hooks/useToast';
import { useAuth } from '@hooks/useAuth';
import type { PassType } from '@/types/enums';
import type { BulkValidationSummaryResponse } from '@/types/gatepass.types';

export default function OnboardingPage() {
  const toast = useToast();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { identity } = useAuth();

  const [file, setFile] = useState<File | null>(null);
  const [passType, setPassType] = useState<PassType>('DAILY');
  const [eventId, setEventId] = useState<string>('');
  const [summary, setSummary] = useState<BulkValidationSummaryResponse | null>(null);
  const [formErrors, setFormErrors] = useState<string[]>([]);

  const events = useQuery({
    queryKey: eventKeys.list({ page: 0, size: 50 }),
    queryFn: () => eventApi.list({ page: 0, size: 50 }),
    enabled: passType === 'EVENT',
  });

  const validate = useMutation({
    mutationFn: () =>
      bulkApi.validate(
        file as File,
        passType,
        passType === 'EVENT' ? Number(eventId) : undefined,
      ),
    onSuccess: setSummary,
    onError: (error) => toast.fromError(error, 'That sheet could not be read.'),
  });

  const confirm = useMutation({
    mutationFn: () =>
      bulkApi.confirm(summary?.batchId as number, {
        confirmed: true,
        confirmedBy: identity?.userId as number,
      }),
    onSuccess: (batch) => {
      void queryClient.invalidateQueries({ queryKey: ['bulk'] });
      navigate(`/faculty/onboarding/batches/${batch.id}`);
    },
    onError: (error) => toast.fromError(error, 'That batch could not be confirmed.'),
  });

  const template = useMutation({
    mutationFn: () => bulkApi.template(passType),
    onSuccess: saveFile,
    onError: (error) => toast.fromError(error, 'The template could not be downloaded.'),
  });

  const errorReport = useMutation({
    mutationFn: () => bulkApi.errorReportUrl(summary?.batchId as number),
    onSuccess: (url) => window.open(url, '_blank', 'noopener'),
    onError: (error) => toast.fromError(error, 'The error report could not be fetched.'),
  });

  const reset = () => { setSummary(null); setFile(null); setFormErrors([]); };

  const startValidate = () => {
    setFormErrors([]);
    if (!file) { setFormErrors(['Choose a sheet first.']); return; }
    if (passType === 'EVENT' && !eventId) {
      setFormErrors(['Choose which event these visitors are attending.']);
      return;
    }
    validate.mutate();
  };

  if (validate.isPending) {
    return (
      <div className="mx-auto max-w-4xl space-y-6 pb-16">
        <div className="rounded-3xl border border-indigo-100 bg-white p-8 shadow-sm space-y-6 text-center">
          <div className="mx-auto flex size-16 items-center justify-center rounded-2xl bg-indigo-50 text-indigo-600 animate-pulse border border-indigo-100">
            <FileSpreadsheet className="size-8" />
          </div>
          <div className="space-y-1">
            <h2 className="font-extrabold text-slate-900 text-xl">Validating Excel Batch Sheet</h2>
            <p className="text-xs text-slate-500 max-w-md mx-auto">
              Checking row formats, duplicate records, and department validity. No passes created yet.
            </p>
          </div>
          <Progress value={0} indeterminate label="Reading rows and verifying addresses…" />
        </div>
      </div>
    );
  }

  if (summary) return (
    <SummaryStep
      summary={summary}
      passType={passType}
      onBack={reset}
      onConfirm={() => confirm.mutate()}
      confirming={confirm.isPending}
      onErrorReport={() => errorReport.mutate()}
      fetchingReport={errorReport.isPending}
    />
  );

  return (
    <div className="mx-auto max-w-4xl space-y-6 pb-16">
      {/* Hero Header */}
      <div className="relative overflow-hidden rounded-3xl border border-indigo-100 bg-gradient-to-r from-slate-900 via-indigo-950 to-slate-900 p-8 text-white shadow-xl">
        <div className="flex flex-wrap items-center justify-between gap-6">
          <div className="space-y-2">
            <div className="inline-flex items-center gap-2 rounded-full bg-white/10 px-3 py-1 text-xs font-semibold text-indigo-200 backdrop-blur-md border border-white/10">
              <Sparkles className="size-3.5 text-indigo-300" /> Batch Pass Issuance
            </div>
            <h1 className="text-3xl font-extrabold text-white sm:text-4xl">Bulk Pass Onboarding</h1>
            <p className="text-sm text-indigo-200/90 max-w-xl">
              Upload an Excel sheet to generate and issue multiple gate passes simultaneously.
            </p>
          </div>

          <Button
            variant="secondary"
            onClick={() => template.mutate()}
            loading={template.isPending}
            className="gap-2 bg-white/10 text-white border-white/10 hover:bg-white/20"
          >
            <Download className="size-4" /> Download Excel Template
          </Button>
        </div>
      </div>

      <div className="rounded-3xl border border-slate-200/80 bg-white p-8 shadow-sm space-y-6">
        <FormError messages={formErrors} />

        <Field label="Pass Category" required>
          {({ id }) => (
            <NativeSelect
              id={id}
              value={passType}
              onChange={(event) => {
                setPassType(event.target.value as PassType);
                setEventId('');
              }}
              className="bg-white font-medium"
            >
              <option value="DAILY">Student Cohort — standing campus daily passes</option>
              <option value="EVENT">Event Visitors — passes bound to a specific event</option>
            </NativeSelect>
          )}
        </Field>

        {passType === 'EVENT' && (
          <Field
            label="Target Campus Event"
            required
            hint="The selected event's validity dates will automatically apply to every attendee."
          >
            {({ id }) => (
              <NativeSelect
                id={id}
                value={eventId}
                disabled={events.isPending}
                onChange={(event) => setEventId(event.target.value)}
                className="bg-white font-medium"
              >
                <option value="">Select an active event…</option>
                {(events.data?.items ?? [])
                  .filter((event) => !event.cancelled)
                  .map((event) => (
                    <option key={event.id} value={event.id}>{event.name}</option>
                  ))}
              </NativeSelect>
            )}
          </Field>
        )}

        <FileDropzone
          rule={UPLOAD_RULES.bulkSheet}
          file={file}
          onSelect={setFile}
          onClear={() => setFile(null)}
        />

        <div className="space-y-2 text-xs text-slate-500">
          <p>
            Required columns: <span className="font-mono text-slate-700">{BULK_COLUMNS.required.join(', ')}</span>.
            Optional: <span className="font-mono text-slate-700">{BULK_COLUMNS.optional.join(', ')}</span>.
            Columns are found by header name, so a Google Form responses sheet can be
            uploaded as exported. Up to {BULK_COLUMNS.maxRows} rows per file.
          </p>

          {/*
            Shown only for EVENT because it describes something that only
            happens there: the sheet is a mixed list, and the person uploading
            it does not know which rows are already members. Saying so here is
            the difference between "why did half of them get a second pass"
            and knowing that is the intended outcome.
          */}
          {passType === 'EVENT' && (
            <p>
              Attendees are matched to existing accounts by email. Someone who already
              has an account keeps it and gains the event pass alongside their current
              one; anyone new gets a visitor account that signs in with an emailed code,
              no password. Both receive their pass and their sign-in details by email.
            </p>
          )}
        </div>

        <div className="flex justify-end pt-2 border-t border-slate-100">
          <Button
            onClick={startValidate}
            disabled={!file}
            className="bg-indigo-600 font-semibold text-white shadow-md shadow-indigo-500/20 hover:bg-indigo-700 px-6"
          >
            Validate & Preview Sheet
          </Button>
        </div>
      </div>
    </div>
  );
}

function SummaryStep({
  summary, passType, onBack, onConfirm, confirming, onErrorReport, fetchingReport,
}: {
  summary: BulkValidationSummaryResponse;
  passType: PassType;
  onBack: () => void;
  onConfirm: () => void;
  confirming: boolean;
  onErrorReport: () => void;
  fetchingReport: boolean;
}) {
  const { totalRows, validRows, invalidRows, errors, awaitingConfirmation } = summary;

  return (
    <div className="mx-auto max-w-4xl space-y-6 pb-16">
      <div className="rounded-3xl border border-slate-200/80 bg-white p-8 shadow-sm space-y-6">
        <div>
          <h2 className="font-extrabold text-slate-900 text-2xl">Sheet Validation Results</h2>
          <p className="text-xs text-slate-500">Verify sheet counts before confirming batch pass creation.</p>
        </div>

        <div className="grid grid-cols-3 gap-4">
          <div className="rounded-2xl border border-slate-200 bg-slate-50 p-5 text-center">
            <dt className="text-xs font-semibold text-slate-500">Total Rows Read</dt>
            <dd className="mt-1 text-3xl font-extrabold text-slate-900">{totalRows}</dd>
          </div>
          <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-5 text-center">
            <dt className="text-xs font-semibold text-emerald-700">Valid Rows</dt>
            <dd className="mt-1 text-3xl font-extrabold text-emerald-800">{validRows}</dd>
          </div>
          <div className="rounded-2xl border border-amber-200 bg-amber-50 p-5 text-center">
            <dt className="text-xs font-semibold text-amber-700">Rows With Errors</dt>
            <dd className="mt-1 text-3xl font-extrabold text-amber-800">{invalidRows}</dd>
          </div>
        </div>

        <p className="text-sm leading-relaxed text-slate-700 bg-slate-50 p-4 rounded-xl border border-slate-200/80">
          {validRows === 0 ? (
            <span className="font-bold text-rose-700">No rows in this sheet could be parsed cleanly. Please correct errors and re-upload.</span>
          ) : (
            <>
              <strong className="font-bold text-slate-900">{validRows}</strong> {passType === 'EVENT' ? 'event passes' : 'daily passes'} will be generated and issued.
              {invalidRows > 0 && (
                <span className="text-slate-500"> The remaining {invalidRows} rows with errors will be skipped.</span>
              )}
            </>
          )}
        </p>
      </div>

      {errors.length > 0 && (
        <div className="rounded-3xl border border-slate-200/80 bg-white p-6 shadow-sm space-y-4">
          <div className="flex items-center justify-between">
            <h3 className="font-bold text-slate-900 text-base">Row Errors Found</h3>
            <Button variant="secondary" size="sm" onClick={onErrorReport} loading={fetchingReport} className="gap-2">
              <FileSpreadsheet className="size-4 text-indigo-600" /> Export Full Error Report
            </Button>
          </div>

          <div className="divide-y divide-slate-100 rounded-2xl border border-slate-200/80 bg-slate-50/50">
            {errors.slice(0, 20).map((row) => (
              <div key={`${row.rowNumber}-${row.email ?? ''}`} className="flex items-center justify-between p-3 px-4 text-xs">
                <span className="font-mono font-bold text-slate-500">Row {row.rowNumber}</span>
                <span className="font-medium text-slate-900 truncate max-w-[200px]">{row.email ?? '—'}</span>
                <span className="text-amber-700 font-semibold">{row.reason}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="flex justify-end gap-3">
        <Button variant="secondary" onClick={onBack}>Upload Another Sheet</Button>
        <Button
          onClick={onConfirm}
          loading={confirming}
          disabled={!awaitingConfirmation}
          className="bg-indigo-600 font-semibold text-white shadow-md shadow-indigo-500/20 hover:bg-indigo-700 px-6"
        >
          Confirm & Issue {validRows} Passes
        </Button>
      </div>
    </div>
  );
}
