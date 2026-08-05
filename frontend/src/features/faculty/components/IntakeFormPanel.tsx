import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Check, Copy, Download, ExternalLink, Info, RefreshCw, Settings2, Share2,
} from 'lucide-react';
import { Button, Field, Input } from '@ui/index';
import { studentImportApi } from '@lib/api/services/user.api';
import { saveFile } from '@lib/api/download';
import { importKeys } from '@lib/query/keys';
import { useToast } from '@hooks/useToast';
import type { ImportBatchResponse } from '@/types/user.types';

/**
 * The campus intake form: where it lives, how to share it, and how to get the
 * responses back.
 *
 * ==========================================================================
 * THE FORM IS NOT CREATED HERE, AND CANNOT BE
 * ==========================================================================
 * A Google Form has to be OWNED by a Google user. This system authenticates as
 * a service account, which cannot own something students can open.
 *
 * So faculty copy a template into their own account, link a responses sheet,
 * and paste both links once. After that, sharing and importing happen here and
 * nobody touches Google again.
 *
 * Creating the form automatically needs the Forms API and OAuth, which means
 * storing a refresh token per faculty member - a credential store this system
 * does not have. Documented as the next step rather than half-built.
 */
/**
 * The questions the form should ask, in order.
 *
 * Mirrors FormColumn in user-service. The matcher there is tolerant of wording
 * — "DOB" and "Date of birth" both resolve — so this is a starting point rather
 * than an exact contract. It matters that it is a starting point somebody can
 * copy rather than a document they have to be sent.
 *
 * NO SEMESTER. It does not exist in this product and must never be added.
 */
const FORM_QUESTIONS: ReadonlyArray<{ label: string; type: string }> = [
  { label: 'Full name', type: 'short text' },
  { label: 'First name', type: 'short text' },
  { label: 'Middle name', type: 'short text, optional' },
  { label: 'Last name', type: 'short text' },
  { label: 'Date of birth', type: 'date' },
  { label: 'Gender', type: 'Male / Female / Other / Prefer not to say' },
  { label: 'Address', type: 'paragraph' },
  { label: 'Phone number', type: 'short text' },
  { label: 'Roll number', type: 'short text' },
  { label: 'Department', type: 'dropdown, your departments' },
  { label: 'Passport photo', type: 'FILE UPLOAD, images only' },
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
    mutationFn: () => studentImportApi.saveSettings({
      formUrl: formUrl.trim() || null,
      responsesSheetUrl: sheetUrl.trim() || null,
    }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: importKeys.settings() });
      setEditing(false);
      toast.success('Form settings saved');
    },
    // The server's message names what to paste instead, so it is shown as-is
    // rather than replaced with something generic.
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

  /**
   * Copy uses the async clipboard API, which needs a secure context - it is
   * unavailable over plain http on anything but localhost. The catch is not
   * defensive noise: on a deployed http box this throws every time, and a
   * silent failure would look like the button does nothing.
   */
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

  /**
   * The question list as plain text, so it can be pasted somewhere useful -
   * a notes app, a message to whoever builds the form, or straight into
   * Google Forms one line at a time.
   */
  const copyQuestions = async () => {
    const text = FORM_QUESTIONS.map((q) => `${q.label}  (${q.type})`).join('\n');
    try {
      await navigator.clipboard.writeText(text);
      setQuestionsCopied(true);
      setTimeout(() => setQuestionsCopied(false), 2000);
    } catch {
      toast.error('Could not copy', 'Select the list and copy it by hand.');
    }
  };

  if (settings.isPending) return null;

  /* ------------------------------------------------------------ set up */

  if (!data?.configured || editing) {
    return (
      <section className="surface-card space-y-[var(--sp-4)] p-[var(--sp-5)]">
        <div className="flex items-start gap-[var(--sp-3)]">
          <Settings2 className="size-5 shrink-0 text-[var(--ink-500)]" aria-hidden />
          <div>
            <h2 className="text-label text-[var(--ink-900)]">Your intake form</h2>
            <p className="text-caption text-[var(--ink-500)]">
              Set this up once. After that you can share the form and import
              responses without leaving this page.
            </p>
          </div>
        </div>

        {/*
          The steps use the SAME numbered chip as the question list below.
          Two ordered lists on one screen in two different visual styles reads
          as two unrelated things, and these are one procedure - do these four
          steps, using that list of questions in step one.

          ml matches the heading's icon gutter (icon + gap), so the whole panel
          shares one left edge instead of the heading sitting inset from its own
          content.
        */}
        <ol className="ml-[calc(var(--sp-5)+var(--sp-3))] max-w-2xl space-y-[var(--sp-3)]">
          {[
            <>Create a Google Form with the questions below.</>,
            <>
              Make the photo question a <strong>file upload</strong>, images only,
              one file. Students pick a picture and Google stores it — they never
              paste a link.
            </>,
            <>In Responses, click the Sheets icon to create the responses spreadsheet.</>,
            <>
              Share that spreadsheet, and the folder Google creates for the uploaded
              photos, with the service account. Read access is enough.
            </>,
          ].map((step, i) => (
            <li key={i} className="flex gap-[var(--sp-3)]">
              <span className="text-caption mt-[2px] inline-flex size-5 shrink-0 items-center justify-center rounded-[var(--r-circle)] bg-[var(--brand-50)] font-medium text-[var(--brand-600)]">
                {i + 1}
              </span>
              <span className="text-body min-w-0 text-[var(--ink-700)]">{step}</span>
            </li>
          ))}
        </ol>

        {/*
          max-w-2xl on the block below.

          Without it the question list runs the full width of a desktop screen,
          which puts the field-type chip several inches from the label it
          belongs to. A list is easier to read narrow than wide - the eye has to
          travel less between the two things it is pairing up.

          THE QUESTIONS THEMSELVES, not a reference to them.

          This panel used to say "the questions listed in the handbook" - and
          there is no handbook in this product. Faculty had no way to discover
          what to type, which made the first step of the setup impossible to
          complete without asking somebody.

          The list is here, in the order the form should ask them, with a copy
          button. The column matcher is tolerant of wording, so these are a
          starting point rather than an exact contract - but a starting point
          somebody can actually use.
        */}
        <div className="ml-[calc(var(--sp-5)+var(--sp-3))] max-w-2xl rounded-[var(--r-md)] bg-[var(--surface-sunken)] p-[var(--sp-4)]">
          <div className="mb-[var(--sp-3)] flex flex-wrap items-center justify-between gap-[var(--sp-2)]">
            <h3 className="text-label text-[var(--ink-900)]">Questions to create</h3>
            <Button variant="secondary" size="sm" onClick={() => void copyQuestions()}>
              {questionsCopied ? <Check aria-hidden /> : <Copy aria-hidden />}
              {questionsCopied ? 'Copied' : 'Copy the list'}
            </Button>
          </div>

          {/*
            NUMBERED, and one per row.

            The previous version was two loose columns of text with the field
            type in grey beside each label, and it read as a paragraph that had
            been broken up rather than a list to work through. Somebody building
            a form needs to know what to add NEXT, so the order has to be
            visible and the rows have to be scannable.

            Numbers rather than bullets because the order matters - it is the
            order the sheet's columns come back in, and a form built in a
            different order still works but is harder to check against.
          */}
          <ol className="divide-y divide-[var(--border)] overflow-hidden rounded-[var(--r-sm)] border border-[var(--border)] bg-[var(--surface)]">
            {FORM_QUESTIONS.map((q, i) => (
              <li
                key={q.label}
                className="flex items-center gap-[var(--sp-3)] px-[var(--sp-3)] py-[var(--sp-2)]"
              >
                <span className="text-caption inline-flex size-5 shrink-0 items-center justify-center rounded-[var(--r-circle)] bg-[var(--surface-sunken)] text-[var(--ink-500)]">
                  {i + 1}
                </span>
                <span className="text-body min-w-0 flex-1 text-[var(--ink-900)]">
                  {q.label}
                </span>
                {/* The type as a chip on the right, so labels stay in one
                    column and the eye can run down them. */}
                <span className="text-caption shrink-0 rounded-[var(--r-pill)] bg-[var(--surface-sunken)] px-[var(--sp-2)] py-[1px] text-[var(--ink-500)]">
                  {q.type}
                </span>
              </li>
            ))}
          </ol>

          <p className="text-caption mt-[var(--sp-3)] flex gap-[var(--sp-2)] text-[var(--ink-500)]">
            <Info className="size-4 shrink-0" aria-hidden />
            <span>
              Google adds <strong>Timestamp</strong> and <strong>Email Address</strong>{' '}
              itself — do not create those. Never ask for a semester.
            </span>
          </p>
        </div>

        {/* max-w so the two fields sit together rather than at opposite ends
            of a wide screen, which reads as two unrelated controls. */}
        <div className="ml-[calc(var(--sp-5)+var(--sp-3))] grid max-w-2xl gap-[var(--sp-4)] sm:grid-cols-2">
          <Field label="Form link" hint="What students open.">
            {({ id, describedBy }) => (
              <Input id={id} aria-describedby={describedBy}
                     placeholder="https://docs.google.com/forms/d/e/.../viewform"
                     value={formUrl} onChange={(e) => setFormUrl(e.target.value)} />
            )}
          </Field>
          <Field label="Responses spreadsheet link" hint="The spreadsheet, not the form.">
            {({ id, describedBy }) => (
              <Input id={id} aria-describedby={describedBy}
                     placeholder="https://docs.google.com/spreadsheets/d/.../edit"
                     value={sheetUrl} onChange={(e) => setSheetUrl(e.target.value)} />
            )}
          </Field>
        </div>

        <div className="flex gap-[var(--sp-3)] border-t border-[var(--border)] pt-[var(--sp-4)]">
          <Button onClick={() => save.mutate()} loading={save.isPending}>Save</Button>
          {data?.configured && (
            <Button variant="ghost" onClick={() => setEditing(false)}>Cancel</Button>
          )}
        </div>
      </section>
    );
  }

  /* --------------------------------------------------------- configured */

  return (
    <section className="surface-card space-y-[var(--sp-4)] p-[var(--sp-5)]">
      <div className="flex flex-wrap items-start justify-between gap-[var(--sp-3)]">
        <div className="min-w-0">
          <h2 className="text-label text-[var(--ink-900)]">Your intake form</h2>
          <p className="text-caption truncate text-[var(--ink-500)]">{data.formUrl}</p>
        </div>
        <Button variant="ghost" size="sm" onClick={() => {
          setFormUrl(data.formUrl ?? '');
          setSheetUrl('');
          setEditing(true);
        }}>
          Change
        </Button>
      </div>

      <div className="flex flex-wrap gap-[var(--sp-2)]">
        <Button variant="secondary" size="sm" onClick={() => void copyLink()}>
          {copied ? <Check aria-hidden /> : <Copy aria-hidden />}
          {copied ? 'Copied' : 'Copy link'}
        </Button>

        {/*
          A plain WhatsApp share URL rather than an integration. It opens their
          own WhatsApp with the message ready, works on desktop and phone, and
          needs no API, no token and no approval.
        */}
        <Button variant="secondary" size="sm" asChild>
          <a
            href={`https://wa.me/?text=${encodeURIComponent(
              `Please fill in your details for your campus pass: ${data.formUrl}`)}`}
            target="_blank"
            rel="noreferrer noopener"
          >
            <Share2 aria-hidden />Share on WhatsApp
          </a>
        </Button>

        <Button variant="ghost" size="sm" asChild>
          <a href={data.formUrl ?? '#'} target="_blank" rel="noreferrer noopener">
            <ExternalLink aria-hidden />Open form
          </a>
        </Button>
      </div>

      <div className="border-t border-[var(--border)] pt-[var(--sp-4)]">
        {data.driveAvailable ? (
          <div className="flex flex-wrap items-center gap-[var(--sp-3)]">
            <Button onClick={() => pull.mutate()} loading={pull.isPending}>
              <RefreshCw aria-hidden />Import latest responses
            </Button>
            <Button variant="ghost" onClick={() => download.mutate()}
                    loading={download.isPending}>
              <Download aria-hidden />Download the sheet
            </Button>
            <span className="text-caption text-[var(--ink-500)]">
              Nothing is created until you confirm.
            </span>
          </div>
        ) : (
          /*
            Drive is off or its credentials did not load. Saying so beats
            offering a button that always fails - and the advice is different
            from "finish setting up your form", which is why the two states are
            separate flags rather than one.
          */
          <p className="text-body text-[var(--ink-500)]">
            This server cannot reach Google Drive, so responses have to be
            downloaded from Google and uploaded below.
          </p>
        )}
      </div>
    </section>
  );
}
