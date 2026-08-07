import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import { Download, FileSpreadsheet } from 'lucide-react';
import { Button, Field, NativeSelect, Progress } from '@ui/index';
import { PageHeader, SectionHeader, StatCard, Stepper } from '@components/data';
import { Alert, FormError } from '@components/feedback';
import { FileDropzone } from '@components/upload';
import { bulkApi, eventApi } from '@lib/api/services/gatepass.api';
import { eventKeys } from '@lib/query/keys';
import { saveFile } from '@lib/api/download';
import { UPLOAD_RULES, BULK_COLUMNS } from '@lib/validation/patterns';
import { useToast } from '@hooks/useToast';
import { useAuth } from '@hooks/useAuth';
import type { PassType } from '@/types/enums';
import type { BulkValidationSummaryResponse } from '@/types/gatepass.types';

const STEPS = ['Choose & upload', 'Review validation', 'Passes issued'];

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

  const header = (
    <PageHeader
      breadcrumbs={[{ label: 'Faculty', to: '/faculty' }, { label: 'Bulk onboarding' }]}
      title="Bulk pass onboarding"
      description="Upload a sheet to generate and issue many gate passes at once."
      actions={
        <Button
          variant="secondary"
          onClick={() => template.mutate()}
          loading={template.isPending}
        >
          <Download aria-hidden /> Download template
        </Button>
      }
    />
  );

  if (validate.isPending) {
    return (
      <div className="flex flex-col gap-[var(--sp-6)]">
        {header}
        <div className="surface-panel px-[var(--sp-6)] py-[var(--sp-6)]">
          <Stepper steps={STEPS} current={0} />
        </div>
        <div className="surface-panel flex flex-col items-center gap-[var(--sp-4)] p-[var(--sp-8)] text-center">
          <span className="flex size-14 animate-pulse items-center justify-center rounded-[var(--r-lg)] bg-[var(--brand-50)]">
            <FileSpreadsheet className="size-6 text-[var(--brand-600)]" aria-hidden />
          </span>
          <div>
            <h2 className="text-h2 text-[var(--ink-900)]">Validating the sheet</h2>
            <p className="text-small mt-[var(--sp-1)] text-[var(--ink-500)]">
              Checking row formats, duplicates and department validity. No passes created yet.
            </p>
          </div>
          <Progress value={0} indeterminate label="Reading rows and verifying addresses…" className="w-full max-w-md" />
        </div>
      </div>
    );
  }

  if (summary) {
    return (
      <SummaryStep
        header={header}
        summary={summary}
        passType={passType}
        onBack={reset}
        onConfirm={() => confirm.mutate()}
        confirming={confirm.isPending}
        onErrorReport={() => errorReport.mutate()}
        fetchingReport={errorReport.isPending}
      />
    );
  }

  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      {header}

      <div className="surface-panel px-[var(--sp-6)] py-[var(--sp-6)]">
        <Stepper steps={STEPS} current={0} />
      </div>

      <div className="surface-panel flex max-w-3xl flex-col gap-[var(--sp-4)] p-[var(--sp-6)]">
        <FormError messages={formErrors} />

        <Field label="Pass category" required>
          {({ id }) => (
            <NativeSelect
              id={id}
              value={passType}
              onChange={(event) => {
                setPassType(event.target.value as PassType);
                setEventId('');
              }}
            >
              <option value="DAILY">Student cohort — standing campus daily passes</option>
              <option value="EVENT">Event visitors — passes bound to a specific event</option>
            </NativeSelect>
          )}
        </Field>

        {passType === 'EVENT' && (
          <Field
            label="Target campus event"
            required
            hint="The event's validity dates apply automatically to every attendee."
          >
            {({ id }) => (
              <NativeSelect
                id={id}
                value={eventId}
                disabled={events.isPending}
                onChange={(event) => setEventId(event.target.value)}
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

        <div className="surface-inset flex flex-col gap-[var(--sp-2)] p-[var(--sp-4)]">
          <p className="text-small text-[var(--ink-700)]">
            Required columns:{' '}
            <span className="text-mono">{BULK_COLUMNS.required.join(', ')}</span>. Optional:{' '}
            <span className="text-mono">{BULK_COLUMNS.optional.join(', ')}</span>. Columns are
            found by header name, so a form responses sheet can be uploaded as exported. Up to{' '}
            {BULK_COLUMNS.maxRows} rows per file.
          </p>

          {/*
            Shown only for EVENT because it describes something that only
            happens there: the sheet is a mixed list, and the person uploading
            it does not know which rows are already members. Saying so here is
            the difference between "why did half of them get a second pass"
            and knowing that is the intended outcome.
          */}
          {passType === 'EVENT' && (
            <p className="text-small text-[var(--ink-500)]">
              Attendees are matched to existing accounts by email. Someone who already has an
              account keeps it and gains the event pass alongside their current one; anyone new
              gets a visitor account that signs in with an emailed code, no password. Both
              receive their pass and sign-in details by email.
            </p>
          )}
        </div>

        <div className="flex justify-end border-t border-[var(--border)] pt-[var(--sp-4)]">
          <Button onClick={startValidate} disabled={!file}>
            Validate & preview
          </Button>
        </div>
      </div>
    </div>
  );
}

function SummaryStep({
  header, summary, passType, onBack, onConfirm, confirming, onErrorReport, fetchingReport,
}: {
  header: React.ReactNode;
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
    <div className="flex flex-col gap-[var(--sp-6)]">
      {header}

      <div className="surface-panel px-[var(--sp-6)] py-[var(--sp-6)]">
        <Stepper steps={STEPS} current={1} />
      </div>

      <section className="flex flex-col gap-[var(--sp-4)]">
        <SectionHeader
          title="Validation results"
          description="Check the counts before confirming that passes are created."
          divided
        />

        <div className="grid gap-[var(--sp-4)] sm:grid-cols-3">
          <StatCard label="Total rows read" value={totalRows} />
          <StatCard label="Valid rows" value={validRows} />
          <StatCard label="Rows with errors" value={invalidRows} />
        </div>

        {validRows === 0 ? (
          <Alert tone="danger" title="No rows could be parsed">
            Correct the errors in the sheet and upload it again.
          </Alert>
        ) : (
          <Alert tone="info" live={false}>
            <strong>{validRows}</strong> {passType === 'EVENT' ? 'event passes' : 'daily passes'}{' '}
            will be generated and issued.
            {invalidRows > 0 && ` The remaining ${invalidRows} rows with errors are skipped.`}
          </Alert>
        )}
      </section>

      {errors.length > 0 && (
        <section className="flex flex-col gap-[var(--sp-4)]">
          <SectionHeader
            title="Row errors"
            description={`${errors.length} row${errors.length === 1 ? '' : 's'} could not be used.`}
            divided
            actions={
              <Button variant="secondary" size="sm" onClick={onErrorReport} loading={fetchingReport}>
                <FileSpreadsheet aria-hidden /> Export full report
              </Button>
            }
          />

          <ul className="surface-panel overflow-hidden">
            {errors.slice(0, 20).map((row) => (
              <li
                key={`${row.rowNumber}-${row.email ?? ''}`}
                className="flex flex-wrap items-center justify-between gap-[var(--sp-2)] border-b border-[var(--border)] px-[var(--sp-4)] py-[var(--sp-3)] last:border-0"
              >
                <span className="text-mono text-[var(--ink-500)]">Row {row.rowNumber}</span>
                <span className="text-small min-w-0 flex-1 truncate text-[var(--ink-900)]">
                  {row.email ?? '—'}
                </span>
                <span className="text-small text-[var(--review-fg)]">{row.reason}</span>
              </li>
            ))}
          </ul>
        </section>
      )}

      <div className="flex justify-end gap-[var(--sp-2)]">
        <Button variant="secondary" onClick={onBack}>Upload another sheet</Button>
        <Button onClick={onConfirm} loading={confirming} disabled={!awaitingConfirmation}>
          Confirm & issue {validRows} passes
        </Button>
      </div>
    </div>
  );
}
