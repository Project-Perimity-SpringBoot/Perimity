import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Check, Copy, Download, ExternalLink, Info, RefreshCw, Settings2, Share2, Sparkles, CheckCircle2, ListOrdered
} from 'lucide-react';
import { Button, Field, Input } from '@ui/index';
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
      <section className="relative overflow-hidden rounded-2xl border border-indigo-100 bg-gradient-to-b from-white via-indigo-50/20 to-white p-6 shadow-sm sm:p-8">
        <div className="flex items-start justify-between gap-4 border-b border-indigo-100/80 pb-6">
          <div className="flex items-center gap-3">
            <div className="flex size-11 items-center justify-center rounded-xl bg-indigo-600 text-white shadow-md shadow-indigo-500/20">
              <Settings2 className="size-5" aria-hidden />
            </div>
            <div>
              <h2 className="text-xl font-bold text-slate-900">Intake Form Setup</h2>
              <p className="text-sm text-slate-500">
                Configure your campus intake form once. Afterwards, import student submissions in one click.
              </p>
            </div>
          </div>
          <div className="inline-flex items-center gap-1.5 rounded-full bg-indigo-50 px-3 py-1 text-xs font-semibold text-indigo-700 border border-indigo-100">
            <Sparkles className="size-3.5" /> Step 1 of 3
          </div>
        </div>

        <div className="mt-6 space-y-6">
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {[
              { num: '1', title: 'Create Google Form', desc: 'Build form using the exact question list below.' },
              { num: '2', title: 'File Upload Photo', desc: 'Set photo question to single file image upload.' },
              { num: '3', title: 'Link Spreadsheet', desc: 'Click Sheets icon in Responses to create spreadsheet.' },
              { num: '4', title: 'Grant Read Access', desc: 'Share sheet & Drive folder with service account.' },
            ].map((step) => (
              <div key={step.num} className="relative rounded-xl border border-slate-200/80 bg-white p-4 shadow-xs">
                <div className="flex items-center gap-2.5">
                  <span className="flex size-7 items-center justify-center rounded-full bg-indigo-100 font-bold text-xs text-indigo-700 border border-indigo-200/80">
                    {step.num}
                  </span>
                  <span className="font-semibold text-sm text-slate-900">{step.title}</span>
                </div>
                <p className="mt-2 text-xs leading-relaxed text-slate-500">{step.desc}</p>
              </div>
            ))}
          </div>

          <div className="rounded-xl border border-slate-200/80 bg-slate-50/80 p-5">
            <div className="mb-4 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <ListOrdered className="size-4 text-indigo-600" />
                <h3 className="font-semibold text-slate-900 text-sm">Required Form Questions</h3>
              </div>
              <Button variant="secondary" size="sm" onClick={() => void copyQuestions()}>
                {questionsCopied ? <Check className="size-3.5 text-emerald-600" /> : <Copy className="size-3.5" />}
                {questionsCopied ? 'Copied' : 'Copy All Questions'}
              </Button>
            </div>

            <div className="grid gap-2.5 sm:grid-cols-2 lg:grid-cols-3">
              {FORM_QUESTIONS.map((q, i) => (
                <div
                  key={q.label}
                  className="flex items-center justify-between rounded-lg border border-slate-200/60 bg-white px-3 py-2 text-xs shadow-2xs"
                >
                  <div className="flex items-center gap-2 min-w-0">
                    <span className="flex size-5 shrink-0 items-center justify-center rounded-full bg-slate-100 font-semibold text-[10px] text-slate-600">
                      {i + 1}
                    </span>
                    <span className="font-medium truncate text-slate-800">{q.label}</span>
                  </div>
                  <span className="shrink-0 rounded-md bg-indigo-50/80 px-2 py-0.5 font-mono text-[10px] text-indigo-700 border border-indigo-100">
                    {q.type}
                  </span>
                </div>
              ))}
            </div>

            <div className="mt-4 flex items-center gap-2 rounded-lg bg-amber-50/80 p-3 text-xs text-amber-800 border border-amber-200/60">
              <Info className="size-4 shrink-0 text-amber-600" aria-hidden />
              <span>
                <strong>Note:</strong> Google Form automatically includes <strong>Timestamp</strong> and <strong>Email Address</strong>. Do not add semester fields.
              </span>
            </div>
          </div>

          <div className="grid gap-5 sm:grid-cols-2">
            <Field label="Form Shareable Link" hint="What students open to fill their details.">
              {({ id, describedBy }) => (
                <Input
                  id={id}
                  aria-describedby={describedBy}
                  placeholder="https://docs.google.com/forms/d/e/.../viewform"
                  value={formUrl}
                  onChange={(e) => setFormUrl(e.target.value)}
                  className="bg-white"
                />
              )}
            </Field>
            <Field label="Responses Spreadsheet Link" hint="The linked Google Sheets URL.">
              {({ id, describedBy }) => (
                <Input
                  id={id}
                  aria-describedby={describedBy}
                  placeholder="https://docs.google.com/spreadsheets/d/.../edit"
                  value={sheetUrl}
                  onChange={(e) => setSheetUrl(e.target.value)}
                  className="bg-white"
                />
              )}
            </Field>
          </div>

          <div className="flex items-center gap-3 border-t border-slate-200/80 pt-4">
            <Button onClick={() => save.mutate()} loading={save.isPending}>
              Save Form Settings
            </Button>
            {data?.configured && (
              <Button variant="ghost" onClick={() => setEditing(false)}>
                Cancel
              </Button>
            )}
          </div>
        </div>
      </section>
    );
  }

  return (
    <section className="relative overflow-hidden rounded-2xl border border-indigo-100 bg-white p-6 shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-100 pb-5">
        <div className="flex items-center gap-3 min-w-0">
          <div className="flex size-10 items-center justify-center rounded-xl bg-emerald-50 text-emerald-600 border border-emerald-200/60">
            <CheckCircle2 className="size-5" />
          </div>
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <h2 className="font-bold text-slate-900 text-base">Intake Form Configured</h2>
              <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-[10px] font-bold text-emerald-800">
                ACTIVE
              </span>
            </div>
            <p className="text-xs truncate text-slate-500 mt-0.5">{data.formUrl}</p>
          </div>
        </div>
        <Button
          variant="secondary"
          size="sm"
          onClick={() => {
            setFormUrl(data.formUrl ?? '');
            setSheetUrl('');
            setEditing(true);
          }}
        >
          Reconfigure Form
        </Button>
      </div>

      <div className="mt-5 flex flex-wrap items-center gap-3">
        <Button variant="secondary" size="sm" onClick={() => void copyLink()}>
          {copied ? <Check className="size-3.5 text-emerald-600" /> : <Copy className="size-3.5" />}
          {copied ? 'Copied Link' : 'Copy Form Link'}
        </Button>

        <Button variant="secondary" size="sm" asChild>
          <a
            href={`https://wa.me/?text=${encodeURIComponent(
              `Please fill in your details for your campus pass: ${data.formUrl}`
            )}`}
            target="_blank"
            rel="noreferrer noopener"
          >
            <Share2 className="size-3.5" /> Share via WhatsApp
          </a>
        </Button>

        <Button variant="ghost" size="sm" asChild>
          <a href={data.formUrl ?? '#'} target="_blank" rel="noreferrer noopener">
            <ExternalLink className="size-3.5" /> Open Form
          </a>
        </Button>
      </div>

      <div className="mt-5 border-t border-slate-100 pt-5">
        {data.driveAvailable ? (
          <div className="flex flex-wrap items-center gap-3">
            <Button onClick={() => pull.mutate()} loading={pull.isPending}>
              <RefreshCw className="size-4" /> Import Latest Responses
            </Button>
            <Button variant="ghost" onClick={() => download.mutate()} loading={download.isPending}>
              <Download className="size-4" /> Download Sheet (.xlsx)
            </Button>
            <span className="text-xs text-slate-500 font-medium">
              ✨ Direct sync active — imports directly from Google Sheets
            </span>
          </div>
        ) : (
          <div className="flex items-center gap-2.5 rounded-lg bg-indigo-50/60 p-3 text-xs text-indigo-900 border border-indigo-100">
            <Info className="size-4 text-indigo-600 shrink-0" />
            <span>
              Direct Drive sync is disabled. Download the Excel file from Google Forms and drag it into the box below.
            </span>
          </div>
        )}
      </div>
    </section>
  );
}
