import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import { Download, FileSpreadsheet } from 'lucide-react';
import { Button, Field, NativeSelect, Progress } from '@ui/index';
import { PageHeader, SectionHeader, StatCard, Stepper } from '@components/data';
import { Alert, FormError } from '@components/feedback';
import { FileDropzone } from '@components/upload';
import { bulkApi, eventApi } from '@lib/api/services/gatepass.api';
import { studentImportApi } from '@lib/api/services/user.api';
import { eventKeys, importKeys } from '@lib/query/keys';
import { saveFile } from '@lib/api/download';
import { UPLOAD_RULES, BULK_COLUMNS, STUDENT_IMPORT_COLUMNS } from '@lib/validation/patterns';
import { useToast } from '@hooks/useToast';
import { useAuth } from '@hooks/useAuth';
import type { PassType } from '@/types/enums';
import type { BulkValidationSummaryResponse } from '@/types/gatepass.types';
import type { ImportBatchResponse, ImportRowResponse } from '@/types/user.types';

const STEPS = ['Choose & upload', 'Review validation', 'Passes issued'];

/*
 * ==========================================================================
 *  ONE SCREEN, TWO ENGINES
 * ==========================================================================
 * The pass category is not a flag passed to one backend. The two categories
 * are served by different services, because they are different jobs:
 *
 *   Event visitors  -> gatepass-service. Issues passes against identities,
 *                      minting a lightweight visitor for anyone new.
 *   Student cohort  -> user-service's student import. Creates campus ACCOUNTS
 *                      with profiles, roll numbers, departments and photos,
 *                      and issues the pass as the last step.
 *
 * Sending a student roster through the gatepass engine is what this screen
 * used to do, and it produced visitor accounts with no password and no
 * profile - people who held a pass but could not sign in and did not appear
 * in Check details. Rather than rebuild the student importer here, this screen
 * calls the one that already exists and is already correct.
 *
 * The three-step shell, the dropzone and the stat cards are shared. Only the
 * API call and the shape of the answer differ, which is why the two summary
 * steps are separate components rather than one with branches through it.
 */
const PAGE_SIZE = { page: 0, size: 100 } as const;

export default function OnboardingPage() {
  const toast = useToast();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { identity } = useAuth();

  const [file, setFile] = useState<File | null>(null);
  const [passType, setPassType] = useState<PassType>('DAILY');
  const [eventId, setEventId] = useState<string>('');
  const [summary, setSummary] = useState<BulkValidationSummaryResponse | null>(null);
  const [studentBatch, setStudentBatch] = useState<ImportBatchResponse | null>(null);
  const [formErrors, setFormErrors] = useState<string[]>([]);

  const isStudent = passType === 'DAILY';

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

  /*
   * Upload AND validate in one call - user-service's importer has no separate
   * validate step, it writes a batch of checked rows and creates nothing until
   * confirm. A FAILED batch is still a successful HTTP call: the answer is
   * "this sheet cannot be used, here is why", which belongs on screen rather
   * than in a toast that disappears.
   */
  const studentUpload = useMutation({
    mutationFn: () => studentImportApi.upload(file as File),
    onSuccess: (batch) => {
      setStudentBatch(batch);
      if (batch.status === 'FAILED' && batch.failureReason) {
        setFormErrors([batch.failureReason]);
      }
    },
    onError: (error) => toast.fromError(error, 'That sheet could not be read.'),
  });

  /*
   * Rejected rows only. A list of 197 rows that were fine is not worth
   * reading, and the person looking at this has the sheet open next to them.
   */
  const studentRows = useQuery({
    queryKey: importKeys.rows(studentBatch?.id as number, 'REJECTED', PAGE_SIZE),
    queryFn: () => studentImportApi.rows(studentBatch?.id as number, 'REJECTED', PAGE_SIZE),
    enabled: studentBatch != null && studentBatch.rejectedCount > 0,
  });

  const studentConfirm = useMutation({
    mutationFn: () => studentImportApi.confirm(studentBatch?.id as number),
    onSuccess: (batch) => {
      setStudentBatch(batch);
      void queryClient.invalidateQueries({ queryKey: importKeys.all });
      if (batch.status !== 'FAILED') {
        toast.success(
          `${batch.createdCount + batch.updatedCount} student(s) imported and passes issued.`,
          batch.missingPhotoCount > 0
            ? `${batch.missingPhotoCount} still need a photo before a pass can be issued.`
            : undefined,
        );
      }
    },
    onError: (error) => toast.fromError(error, 'That batch could not be confirmed.'),
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

  const reset = () => {
    setSummary(null);
    setStudentBatch(null);
    setFile(null);
    setFormErrors([]);
  };

  const startValidate = () => {
    setFormErrors([]);
    if (!file) { setFormErrors(['Choose a sheet first.']); return; }
    if (isStudent) { studentUpload.mutate(); return; }
    if (!eventId) {
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
      /*
        No template for a student cohort. That sheet is a Google Form export -
        the form itself is the template, and it is set up under Import
        students. Offering a four-column download here would hand faculty a
        file that is rejected the moment they upload it.
      */
      actions={
        passType === 'EVENT' ? (
          <Button
            variant="secondary"
            onClick={() => template.mutate()}
            loading={template.isPending}
          >
            <Download aria-hidden /> Download template
          </Button>
        ) : null
      }
    />
  );

  if (validate.isPending || studentUpload.isPending) {
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
              {isStudent
                ? 'Checking every column, row formats, duplicate roll numbers and department validity. No accounts created yet.'
                : 'Checking row formats, duplicates and department validity. No passes created yet.'}
            </p>
          </div>
          <Progress value={0} indeterminate label="Reading rows and verifying addresses…" className="w-full max-w-md" />
        </div>
      </div>
    );
  }

  if (studentBatch) {
    return (
      <StudentSummaryStep
        header={header}
        batch={studentBatch}
        rejected={studentRows.data?.items ?? []}
        onBack={reset}
        onConfirm={() => studentConfirm.mutate()}
        confirming={studentConfirm.isPending}
      />
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
                // The two categories do not share a batch. Carrying one over
                // would show event counts above a student sheet.
                setSummary(null);
                setStudentBatch(null);
                setFormErrors([]);
              }}
            >
              <option value="DAILY">Student cohort — accounts and standing campus passes</option>
              <option value="EVENT">Event visitors — passes bound to a specific event</option>
            </NativeSelect>
          )}
        </Field>

        {!isStudent && (
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
          {/*
            The two categories want different sheets, so the hint has to change
            with the category. Showing "required: name, email" above a student
            upload would be actively wrong - that sheet is rejected outright
            without a roll number, a department and a photo.
          */}
          {isStudent ? (
            <p className="text-small text-[var(--ink-700)]">
              Required columns:{' '}
              <span className="text-mono">{STUDENT_IMPORT_COLUMNS.required.join(', ')}</span>.
              Optional:{' '}
              <span className="text-mono">{STUDENT_IMPORT_COLUMNS.optional.join(', ')}</span>.
              Columns are found by header name, so the responses sheet can be uploaded exactly as
              Google Forms exported it.
            </p>
          ) : (
            <p className="text-small text-[var(--ink-700)]">
              Required columns:{' '}
              <span className="text-mono">{BULK_COLUMNS.required.join(', ')}</span>. Optional:{' '}
              <span className="text-mono">{BULK_COLUMNS.optional.join(', ')}</span>. Columns are
              found by header name, so a form responses sheet can be uploaded as exported. Up to{' '}
              {BULK_COLUMNS.maxRows} rows per file.
            </p>
          )}

          {isStudent && (
            <p className="text-small text-[var(--ink-500)]">
              Each row becomes a campus account with a profile, roll number and department, then a
              standing pass. Students already on this campus are matched by email and updated
              rather than duplicated. The passport photo is a Drive link from the form; a student
              imported without one keeps their account but cannot hold a pass until it arrives.
            </p>
          )}

          {/*
            Shown only for EVENT because it describes something that only
            happens there: the sheet is a mixed list, and the person uploading
            it does not know which rows are already members. Saying so here is
            the difference between "why did half of them get a second pass"
            and knowing that is the intended outcome.
          */}
          {!isStudent && (
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
            {isStudent ? 'Validate sheet' : 'Validate & preview'}
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

/**
 * The student cohort's review step, and its result.
 *
 * ==========================================================================
 *  ONE COMPONENT FOR BOTH, BECAUSE THE BATCH ONLY MOVES FORWARD
 * ==========================================================================
 * Before confirm it reports what WOULD happen; after confirm it reports what
 * DID. The same batch object answers both, and which one is on screen is
 * derived from its status rather than tracked in a second piece of state that
 * could disagree with it.
 *
 * The counts are deliberately not collapsed into one "success" number.
 * Created, updated and imported-without-a-photo are three different outcomes
 * with three different follow-ups, and a student with no photo cannot hold a
 * pass - burying that in a total is how somebody turns up at a gate with
 * nothing to show.
 */
function StudentSummaryStep({
  header, batch, rejected, onBack, onConfirm, confirming,
}: {
  header: React.ReactNode;
  batch: ImportBatchResponse;
  rejected: ImportRowResponse[];
  onBack: () => void;
  onConfirm: () => void;
  confirming: boolean;
}) {
  const { totalRows, validRows, rejectedCount, missingPhotoCount, createdCount, updatedCount } = batch;
  const written = createdCount + updatedCount;
  const done = batch.status === 'COMPLETED' || written > 0;

  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      {header}

      <div className="surface-panel px-[var(--sp-6)] py-[var(--sp-6)]">
        <Stepper steps={STEPS} current={done ? 2 : 1} />
      </div>

      <section className="flex flex-col gap-[var(--sp-4)]">
        <SectionHeader
          title={done ? 'Import results' : 'Validation results'}
          description={
            done
              ? 'Accounts have been created and passes issued.'
              : 'Check the counts before creating accounts and issuing passes.'
          }
          divided
        />

        <div className="grid gap-[var(--sp-4)] sm:grid-cols-3">
          <StatCard label="Total rows read" value={totalRows} />
          <StatCard label={done ? 'Students imported' : 'Valid rows'} value={done ? written : validRows} />
          <StatCard label="Rows with errors" value={rejectedCount} />
        </div>

        {batch.status === 'FAILED' && batch.failureReason && (
          <Alert tone="danger" title="That sheet could not be used">
            {batch.failureReason}
          </Alert>
        )}

        {!done && validRows === 0 && batch.status !== 'FAILED' && (
          <Alert tone="danger" title="No rows could be used">
            Correct the errors in the sheet and upload it again.
          </Alert>
        )}

        {!done && validRows > 0 && (
          <Alert tone="info" live={false}>
            <strong>{validRows}</strong> student account(s) will be created or updated, and a
            standing campus pass issued to each.
            {rejectedCount > 0 && ` The remaining ${rejectedCount} rows with errors are skipped.`}
          </Alert>
        )}

        {done && missingPhotoCount > 0 && (
          <Alert tone="warning" live={false} title={`${missingPhotoCount} imported without a photo`}>
            These students have an account and verified details, but cannot hold a pass until a
            photo exists. They can add one from their own profile.
          </Alert>
        )}

        {done && rejectedCount === 0 && missingPhotoCount === 0 && written > 0 && (
          <Alert tone="success" live={false} title="Every row imported">
            All {written} student(s) have an account and a pass.
          </Alert>
        )}
      </section>

      {rejected.length > 0 && (
        <section className="flex flex-col gap-[var(--sp-4)]">
          <SectionHeader
            title="Row errors"
            description={`${rejectedCount} row${rejectedCount === 1 ? '' : 's'} could not be used.`}
            divided
          />

          <ul className="surface-panel overflow-hidden">
            {rejected.slice(0, 20).map((row) => (
              <li
                key={row.id}
                className="flex flex-wrap items-center justify-between gap-[var(--sp-2)] border-b border-[var(--border)] px-[var(--sp-4)] py-[var(--sp-3)] last:border-0"
              >
                <span className="text-mono text-[var(--ink-500)]">Row {row.rowNumber}</span>
                <span className="text-small min-w-0 flex-1 truncate text-[var(--ink-900)]">
                  {row.email ?? row.fullName ?? '—'}
                </span>
                <span className="text-small text-[var(--review-fg)]">{row.message ?? 'Rejected'}</span>
              </li>
            ))}
          </ul>
        </section>
      )}

      <div className="flex justify-end gap-[var(--sp-2)]">
        <Button variant="secondary" onClick={onBack}>
          {done ? 'Upload another sheet' : 'Choose a different sheet'}
        </Button>

        {/*
          confirmable is true for FAILED as well as VALIDATED: a confirm that
          died partway is resumable and picks up only the rows never written,
          which is why the label changes rather than the button disappearing.
        */}
        {!done && batch.confirmable && validRows > 0 && (
          <Button onClick={onConfirm} loading={confirming}>
            {batch.status === 'FAILED'
              ? 'Resume importing'
              : `Create ${validRows} account(s) & issue passes`}
          </Button>
        )}
      </div>
    </div>
  );
}
