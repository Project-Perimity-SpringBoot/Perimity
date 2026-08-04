import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import { Download, FileSpreadsheet } from 'lucide-react';
import { Button, Field, NativeSelect, Progress } from '@ui/index';
import { PageHeader } from '@components/data';
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

/**
 * Phase 4 screens 5, 6 and 7 — upload, validating, summary.
 *
 * ==========================================================================
 * THREE STEPS, ONE SCREEN, ON PURPOSE
 * ==========================================================================
 * They are one continuous decision the user does not leave: choose a file,
 * wait ~2 seconds, look at what it found, confirm. Splitting them across routes
 * would put a back button in the middle of a wizard whose middle step holds a
 * server-side batch id that a fresh page load cannot recover.
 *
 * Step 4 is a different matter and lives at its own URL — see
 * BatchProgressPage. That one is asynchronous, minutes long, and the user is
 * told they may close the page, so it MUST be reachable by URL.
 *
 * ==========================================================================
 * VALIDATE CREATES NOTHING
 * ==========================================================================
 * /bulk/validate returns counts and row errors. No identity, no pass, no email.
 * That is what makes the summary honest: the user is looking at what WOULD
 * happen, and nothing has happened yet. Confirm is the only step that writes.
 */
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

  /** Only EVENT batches need an event. Not fetched at all for DAILY. */
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
        // @AssertTrue server-side: it must literally be true.
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

  /* ── Step 2. Synchronous, ~2s, and the user waits. Indeterminate because the
        server reports nothing until it has read the whole sheet — a percentage
        invented on the client would be a lie that happens to look reassuring. */
  if (validate.isPending) {
    return (
      <div className="flex flex-col gap-[var(--sp-6)]">
        <PageHeader title="Checking your sheet" />
        <div className="surface-card flex flex-col gap-[var(--sp-4)] p-[var(--sp-6)]">
          <Progress value={0} indeterminate label="Reading rows and checking each address…" />
          <p className="text-caption text-[var(--ink-500)]">
            Nothing has been created yet. This only reads the file.
          </p>
        </div>
      </div>
    );
  }

  /* ── Step 3. The summary. ── */
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

  /* ── Step 1. Upload. ── */
  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      <PageHeader
        title="Bulk onboarding"
        description="Create many passes from one sheet. Nothing is created until you confirm."
        actions={
          <Button variant="secondary" onClick={() => template.mutate()} loading={template.isPending}>
            <Download aria-hidden />Template
          </Button>
        }
      />

      <div className="surface-card flex flex-col gap-[var(--sp-5)] p-[var(--sp-6)]">
        <FormError messages={formErrors} />

        <Field label="What are you creating" required>
          {({ id }) => (
            <NativeSelect
              id={id}
              value={passType}
              onChange={(event) => {
                setPassType(event.target.value as PassType);
                setEventId('');
              }}
            >
              <option value="DAILY">Students — a standing daily pass each</option>
              <option value="EVENT">Event visitors — passes for one programme</option>
            </NativeSelect>
          )}
        </Field>

        {passType === 'EVENT' ? (
          <Field
            label="Which event"
            required
            hint="The event's own dates apply to every attendee in the sheet. The sheet carries no dates."
          >
            {({ id }) => (
              <NativeSelect
                id={id}
                value={eventId}
                disabled={events.isPending}
                onChange={(event) => setEventId(event.target.value)}
              >
                <option value="">Choose an event…</option>
                {(events.data?.items ?? [])
                  .filter((event) => !event.cancelled)
                  .map((event) => (
                    <option key={event.id} value={event.id}>{event.name}</option>
                  ))}
              </NativeSelect>
            )}
          </Field>
        ) : null}

        <FileDropzone
          rule={UPLOAD_RULES.bulkSheet}
          file={file}
          onSelect={setFile}
          onClear={() => setFile(null)}
        />

        <p className="text-caption text-[var(--ink-500)]">
          Required columns: {BULK_COLUMNS.required.join(', ')}. Optional:{' '}
          {BULK_COLUMNS.optional.join(', ')}. Column order does not matter and up to{' '}
          {BULK_COLUMNS.maxRows} rows are accepted.
        </p>

        <div className="flex justify-end">
          <Button onClick={startValidate} disabled={!file}>Check the sheet</Button>
        </div>
      </div>
    </div>
  );
}

/**
 * Step 3.
 *
 * ==========================================================================
 * THE ARITHMETIC MUST RECONCILE, VISIBLY
 * ==========================================================================
 * total = valid + invalid, and the number of passes that will be created is
 * `valid` — never `total`. Somebody uploads 600 rows, sees "600 passes will be
 * created", and tells 600 people to turn up; 20 of them have no pass. So the
 * three numbers are shown together and the sentence underneath states the one
 * that matters.
 */
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
    <div className="flex flex-col gap-[var(--sp-6)]">
      <PageHeader
        title="What the sheet contains"
        description="Nothing has been created yet. Check the numbers, then confirm."
      />

      <div className="surface-card p-[var(--sp-6)]">
        <dl className="grid grid-cols-3 gap-[var(--sp-4)]">
          <Figure label="Rows read" value={totalRows} />
          <Figure label="Valid" value={validRows} />
          <Figure label="With errors" value={invalidRows} />
        </dl>

        <p className="text-body mt-[var(--sp-5)] text-[var(--ink-700)]">
          {validRows === 0 ? (
            <>No row in this sheet can be used. Fix the errors and upload it again.</>
          ) : (
            <>
              <strong className="text-[var(--ink-900)]">{validRows}</strong>{' '}
              {passType === 'EVENT' ? 'event passes' : 'daily passes'} will be created and emailed.
              {invalidRows > 0 ? (
                <> The other {invalidRows} {invalidRows === 1 ? 'row is' : 'rows are'} skipped
                  entirely — nobody in them is told anything.</>
              ) : null}
            </>
          )}
        </p>
      </div>

      {errors.length > 0 ? (
        <section aria-labelledby="row-errors" className="surface-card overflow-hidden">
          <div className="flex items-baseline justify-between gap-[var(--sp-4)] p-[var(--sp-5)]">
            <h2 id="row-errors" className="text-h3 text-[var(--ink-900)]">Rows with errors</h2>
            <Button variant="secondary" onClick={onErrorReport} loading={fetchingReport}>
              <FileSpreadsheet aria-hidden />Full report
            </Button>
          </div>
          <ul className="divide-y divide-[var(--border)] border-t border-[var(--border)]">
            {errors.slice(0, 20).map((row) => (
              <li
                key={`${row.rowNumber}-${row.email ?? ''}`}
                className="flex flex-wrap items-baseline gap-x-[var(--sp-4)] gap-y-[var(--sp-1)] p-[var(--sp-4)]"
              >
                <span className="text-caption font-mono text-[var(--ink-500)]">
                  Row {row.rowNumber}
                </span>
                <span className="text-body min-w-0 flex-1 truncate text-[var(--ink-900)]">
                  {row.email ?? '—'}
                </span>
                <span className="text-caption text-[var(--ink-700)]">{row.reason}</span>
              </li>
            ))}
          </ul>
          {errors.length > 20 ? (
            <p className="text-caption border-t border-[var(--border)] p-[var(--sp-4)] text-[var(--ink-500)]">
              Showing the first 20 of {errors.length}. Download the report for the rest.
            </p>
          ) : null}
        </section>
      ) : null}

      <div className="flex flex-wrap justify-end gap-[var(--sp-3)]">
        <Button variant="secondary" onClick={onBack}>Choose another sheet</Button>
        <Button onClick={onConfirm} loading={confirming} disabled={!awaitingConfirmation}>
          Create {validRows} {validRows === 1 ? 'pass' : 'passes'}
        </Button>
      </div>
    </div>
  );
}

function Figure({ label, value }: { label: string; value: number }) {
  return (
    <div>
      <dt className="text-label text-[var(--ink-500)]">{label}</dt>
      <dd className="text-h2 text-[var(--ink-900)]">{value}</dd>
    </div>
  );
}
