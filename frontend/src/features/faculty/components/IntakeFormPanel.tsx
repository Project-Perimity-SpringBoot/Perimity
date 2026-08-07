import { useState } from 'react';
import {
  Check, CheckCircle2, Copy, Download, ExternalLink, Info, ListOrdered,
  RefreshCw, Settings2, Share2,
} from 'lucide-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Badge, Button, Field, Input } from '@ui/index';
import { SectionHeader } from '@components/data';
import { Alert } from '@components/feedback';
import { studentImportApi } from '@lib/api/services/user.api';
import { saveFile } from '@lib/api/download';
import { importKeys } from '@lib/query/keys';
import { useToast } from '@hooks/useToast';
import type { ImportBatchResponse } from '@/types/user.types';

const FORM_QUESTIONS: ReadonlyArray<{ label: string; type: string; required: boolean }> = [
  { label: 'Full name', type: 'Short text', required: true },
  { label: 'First name', type: 'Short text', required: true },
  { label: 'Middle name', type: 'Short text', required: false },
  { label: 'Last name', type: 'Short text', required: true },
  { label: 'Date of birth', type: 'Date', required: true },
  { label: 'Gender', type: 'Multiple choice', required: true },
  { label: 'Address', type: 'Paragraph', required: true },
  { label: 'Phone number', type: 'Short text', required: true },
  { label: 'Roll number', type: 'Short text', required: true },
  { label: 'Department', type: 'Dropdown (Campus)', required: true },
  { label: 'Passport photo', type: 'File Upload (Images)', required: true },
];

const SETUP_STEPS: ReadonlyArray<{ num: string; title: string; desc: string }> = [
  { num: '1', title: 'Create the form', desc: 'Build it using the exact question list below.' },
  { num: '2', title: 'Photo upload', desc: 'Set the photo question to a single image upload.' },
  { num: '3', title: 'Link a sheet', desc: 'In Responses, click the Sheets icon to create one.' },
  { num: '4', title: 'Grant read access', desc: 'Share the sheet and Drive folder with the service account.' },
];

export function IntakeFormPanel({ onPulled }: { onPulled: (b: ImportBatchResponse) => void }) {
  const toast = useToast();
  const queryClient = useQueryClient();

  const [editing, setEditing] = useState(false);
  const [formUrl, setFormUrl] = useState('');
  const [sheetUrl, setSheetUrl] = useState('');
  const [copied, setCopied] = useState(false);
  const [questionsCopied, setQuestionsCopied] = useState(false);

  const settings = useQuery({
    queryKey: importKeys.settings(),
    queryFn: () => studentImportApi.settings(),
  });

  const save = useMutation({
    mutationFn: () =>
      studentImportApi.saveSettings({
        formUrl: formUrl.trim() || null,
        responsesSheetUrl: sheetUrl.trim() || null,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: importKeys.settings() });
      setEditing(false);
      toast.success('Form settings saved');
    },
    onError: (error) => toast.fromError(error, 'Those links could not be saved.'),
  });

  const pull = useMutation({
    mutationFn: () => studentImportApi.pull(),
    onSuccess: (batch) => {
      onPulled(batch);
      toast.success(`Checked ${batch.totalRows} responses`);
    },
    onError: (error) => toast.fromError(error, 'The responses could not be read.'),
  });

  const download = useMutation({
    mutationFn: () => studentImportApi.download(),
    onSuccess: (file) => saveFile(file),
    onError: (error) => toast.fromError(error, 'The sheet could not be downloaded.'),
  });

  const data = settings.data;

  const copyLink = async () => {
    if (!data?.formUrl) return;
    try {
      await navigator.clipboard.writeText(data.formUrl);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      toast.error('Could not copy', 'Select the link and copy it by hand.');
    }
  };

  const copyQuestions = async () => {
    const text = FORM_QUESTIONS.map((q) => `${q.label}  (${q.type}${q.required ? '' : ', optional'})`).join('\n');
    try {
      await navigator.clipboard.writeText(text);
      setQuestionsCopied(true);
      setTimeout(() => setQuestionsCopied(false), 2000);
    } catch {
      toast.error('Could not copy', 'Select the list and copy it by hand.');
    }
  };

  if (settings.isPending) return null;

  if (!data?.configured || editing) {
    return (
      <section className="surface-panel flex flex-col gap-[var(--sp-6)] p-[var(--sp-6)]">
        <SectionHeader
          icon={Settings2}
          title="Intake form setup"
          description="Configure your campus intake form once. After that, importing submissions is one click."
          divided
        />

        <div className="grid gap-[var(--sp-3)] sm:grid-cols-2 lg:grid-cols-4">
          {SETUP_STEPS.map((step) => (
            <div key={step.num} className="surface-inset flex flex-col gap-[var(--sp-2)] p-[var(--sp-4)]">
              <div className="flex items-center gap-[var(--sp-2)]">
                <span className="text-caption flex size-6 shrink-0 items-center justify-center rounded-[var(--r-circle)] bg-[var(--brand-600)] text-white">
                  {step.num}
                </span>
                <span className="text-body-md text-[var(--ink-900)]">{step.title}</span>
              </div>
              <p className="text-small text-[var(--ink-500)]">{step.desc}</p>
            </div>
          ))}
        </div>

        <div className="surface-inset flex flex-col gap-[var(--sp-4)] p-[var(--sp-4)]">
          <SectionHeader
            icon={ListOrdered}
            title="Required form questions"
            actions={
              <Button variant="secondary" size="sm" onClick={() => void copyQuestions()}>
                {questionsCopied ? <Check aria-hidden /> : <Copy aria-hidden />}
                {questionsCopied ? 'Copied' : 'Copy all'}
              </Button>
            }
          />

          <ul className="grid gap-[var(--sp-2)] sm:grid-cols-2 lg:grid-cols-3">
            {FORM_QUESTIONS.map((q, i) => (
              <li
                key={q.label}
                className="flex items-center justify-between gap-[var(--sp-2)] rounded-[var(--r-sm)] border border-[var(--border)] bg-[var(--surface)] px-[var(--sp-3)] py-[var(--sp-2)]"
              >
                <span className="flex min-w-0 items-center gap-[var(--sp-2)]">
                  <span className="text-caption flex size-5 shrink-0 items-center justify-center rounded-[var(--r-circle)] bg-[var(--surface-sunken)] text-[var(--ink-500)]">
                    {i + 1}
                  </span>
                  <span className="text-small truncate text-[var(--ink-900)]">{q.label}</span>
                </span>
                <Badge className="shrink-0">{q.type}</Badge>
              </li>
            ))}
          </ul>

          <Alert tone="info" live={false}>
            The form automatically includes <strong>Timestamp</strong> and{' '}
            <strong>Email Address</strong>. Do not add semester fields.
          </Alert>
        </div>

        <div className="grid gap-[var(--sp-4)] sm:grid-cols-2">
          <Field label="Form shareable link" hint="What students open to fill in their details.">
            {({ id, describedBy }) => (
              <Input
                id={id}
                aria-describedby={describedBy}
                placeholder="https://docs.google.com/forms/d/e/.../viewform"
                value={formUrl}
                onChange={(e) => setFormUrl(e.target.value)}
              />
            )}
          </Field>
          <Field label="Responses spreadsheet link" hint="The linked Google Sheets URL.">
            {({ id, describedBy }) => (
              <Input
                id={id}
                aria-describedby={describedBy}
                placeholder="https://docs.google.com/spreadsheets/d/.../edit"
                value={sheetUrl}
                onChange={(e) => setSheetUrl(e.target.value)}
              />
            )}
          </Field>
        </div>

        <div className="flex items-center justify-end gap-[var(--sp-2)] border-t border-[var(--border)] pt-[var(--sp-4)]">
          {data?.configured && (
            <Button variant="ghost" onClick={() => setEditing(false)}>Cancel</Button>
          )}
          <Button onClick={() => save.mutate()} loading={save.isPending}>
            Save form settings
          </Button>
        </div>
      </section>
    );
  }

  return (
    <section className="surface-panel flex flex-col gap-[var(--sp-4)] p-[var(--sp-6)]">
      <SectionHeader
        icon={CheckCircle2}
        title="Intake form configured"
        description={data.formUrl ?? undefined}
        badge={<Badge tone="brand">Active</Badge>}
        divided
        actions={
          <Button
            variant="secondary"
            size="sm"
            onClick={() => {
              setFormUrl(data.formUrl ?? '');
              setSheetUrl('');
              setEditing(true);
            }}
          >
            Reconfigure
          </Button>
        }
      />

      <div className="flex flex-wrap items-center gap-[var(--sp-2)]">
        <Button variant="secondary" size="sm" onClick={() => void copyLink()}>
          {copied ? <Check aria-hidden /> : <Copy aria-hidden />}
          {copied ? 'Copied' : 'Copy form link'}
        </Button>

        <Button variant="secondary" size="sm" asChild>
          <a
            href={`https://wa.me/?text=${encodeURIComponent(
              `Please fill in your details for your campus pass: ${data.formUrl}`
            )}`}
            target="_blank"
            rel="noreferrer noopener"
          >
            <Share2 aria-hidden /> Share via WhatsApp
          </a>
        </Button>

        <Button variant="ghost" size="sm" asChild>
          <a href={data.formUrl ?? '#'} target="_blank" rel="noreferrer noopener">
            <ExternalLink aria-hidden /> Open form
          </a>
        </Button>
      </div>

      <div className="border-t border-[var(--border)] pt-[var(--sp-4)]">
        {data.driveAvailable ? (
          <div className="flex flex-wrap items-center gap-[var(--sp-2)]">
            <Button onClick={() => pull.mutate()} loading={pull.isPending}>
              <RefreshCw aria-hidden /> Import latest responses
            </Button>
            <Button variant="ghost" onClick={() => download.mutate()} loading={download.isPending}>
              <Download aria-hidden /> Download sheet (.xlsx)
            </Button>
            <span className="text-small text-[var(--ink-500)]">
              Direct sync is active — responses import straight from the sheet.
            </span>
          </div>
        ) : (
          <Alert tone="info" icon={Info} live={false}>
            Direct Drive sync is disabled. Download the Excel file from the form and drop it into
            the upload box below.
          </Alert>
        )}
      </div>
    </section>
  );
}
