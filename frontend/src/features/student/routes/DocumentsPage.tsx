import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router';
import { BadgeCheck, ExternalLink, FileText, Trash2 } from 'lucide-react';
import { Badge, Button, Field, NativeSelect, SkeletonText } from '@ui/index';
import { ConfirmDialog, EmptyState, ErrorState } from '@components/feedback';
import { PageHeader } from '@components/data';
import { FileDropzone } from '@components/upload';
import { documentApi } from '@lib/api/services/user.api';
import { documentKeys } from '@lib/query/keys';
import { formatDateTime } from '@lib/format/datetime';
import { UPLOAD_RULES } from '@lib/validation/patterns';
import type { DocumentType } from '@/types/enums';
import type { DocumentResponse } from '@/types/user.types';
import { useAuth } from '@hooks/useAuth';
import { useToast } from '@hooks/useToast';

const TYPE_LABEL: Record<DocumentType, string> = {
  PHOTO: 'Photo',
  ID_PROOF: 'ID proof',
  CERTIFICATE: 'Certificate',
  OTHER: 'Other',
};

/**
 * PHOTO is a valid DocumentType and is NOT offered here.
 *
 * There are two different photos in this product and they are not
 * interchangeable:
 *
 *   the passport photo  -> StudentProfile.photoS3Key, uploaded on My details,
 *                          shown to a GUARD at the gate, and required before
 *                          details can be submitted
 *   a PHOTO document    -> a Document row, reviewed by staff like any other
 *                          attachment, and invisible at the gate
 *
 * Offering "Photo" in this dropdown meant a student could upload their
 * passport photo here, believe the job was done, and still be blocked from
 * submitting their details for a missing photo - with nothing on either screen
 * explaining why. TYPE_LABEL keeps the entry because the server can still
 * return PHOTO documents and the list below has to label them.
 */
const UPLOADABLE_TYPES: DocumentType[] = ['ID_PROOF', 'CERTIFICATE', 'OTHER'];

/**
 * Phase 3 screen 8 — documents.
 *
 * Verification is one-way from here: a student uploads, staff decide. There is
 * no self-verify control because there is no endpoint for one, and a disabled
 * button implying otherwise would misrepresent who is in charge of it.
 *
 * A rejected document keeps its remarks and stays on the list. The student
 * needs to read why before uploading a replacement, and hiding it would leave
 * them guessing at what to fix.
 *
 * Files open through a short-lived presigned URL in a new tab — the bytes never
 * pass through this application.
 */
export default function DocumentsPage() {
  const { identity } = useAuth();
  const toast = useToast();
  const queryClient = useQueryClient();

  const [docType, setDocType] = useState<DocumentType>('ID_PROOF');
  const [file, setFile] = useState<File | null>(null);
  const [removing, setRemoving] = useState<DocumentResponse | null>(null);

  const documents = useQuery({
    queryKey: documentKeys.mine(),
    queryFn: () => documentApi.mine(),
  });

  const upload = useMutation({
    mutationFn: (chosen: File) =>
      documentApi.upload(identity?.userId as number, docType, chosen),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: documentKeys.mine() });
      toast.success('Uploaded — staff will review it');
      setFile(null);
    },
    onError: (error) => toast.fromError(error, 'That document could not be uploaded.'),
  });

  /**
   * OPENING A FILE, AND WHY THE BLANK TAB IS OPENED FIRST
   *
   * window.open is only permitted while a user gesture is still being handled.
   * This URL has to be fetched first - it is minted per request and expires -
   * so calling window.open in onSuccess runs AFTER the round trip, by which
   * point the gesture is spent and Chrome, Safari and Firefox all block it.
   *
   * It appears to work on localhost, which most blockers allowlist, and then
   * fails silently for real users: no error, no tab, nothing to report.
   *
   * So the tab is opened synchronously inside the click, while the gesture is
   * live, and its location is set once the URL arrives. If the fetch fails the
   * blank tab is closed again rather than left sitting there.
   */
  const open = useMutation({
    mutationFn: (id: number) => documentApi.downloadUrl(id),
    onError: (error) => toast.fromError(error, 'That link could not be created.'),
  });

  const openDocument = (id: number) => {
    const tab = window.open('', '_blank', 'noopener,noreferrer');
    open.mutate(id, {
      onSuccess: (presigned) => {
        if (tab) tab.location.href = presigned.url;
        else window.location.href = presigned.url;
      },
      onError: () => tab?.close(),
    });
  };

  const remove = useMutation({
    mutationFn: (id: number) => documentApi.remove(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: documentKeys.mine() });
      toast.success('Document removed');
      setRemoving(null);
    },
    onError: (error) => toast.fromError(error, 'That document could not be removed.'),
  });

  if (documents.isError) {
    return <ErrorState error={documents.error} onRetry={() => void documents.refetch()} />;
  }

  const items: DocumentResponse[] = documents.data ?? [];

  /** Awaiting review = not verified and nobody has written remarks on it yet. */
  const isAwaitingReview = (d: DocumentResponse): boolean =>
    !d.verified && !d.verificationRemarks;

  const pendingSameType = items.some((d) => d.docType === docType && isAwaitingReview(d));

  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      <PageHeader
        title="Documents"
        description="Optional. Your passport photo on My details is the one that is required — these are extra proof staff can check."
      />

      <section className="surface-card flex flex-col gap-[var(--sp-4)] p-[var(--sp-6)]">
        <h2 className="text-h3 text-[var(--ink-900)]">Upload a document</h2>

        {/*
          The hint sits ABOVE the select, not under it as Field's `hint` renders
          it. An open native dropdown covers whatever is directly beneath, so a
          note placed there is hidden at the exact moment someone is choosing a
          type - which is the only moment it is useful.
        */}
        <p className="text-small text-[var(--ink-700)]">
          Your passport photo is not uploaded here — it goes on{' '}
          <Link className="underline" to="/student/profile/details">My details</Link>, where
          the guard&rsquo;s copy comes from.
        </p>

        <Field label="What is it?">
          {({ id }) => (
            <NativeSelect
              id={id}
              className="max-w-64"
              value={docType}
              onChange={(e) => setDocType(e.target.value as DocumentType)}
            >
              {UPLOADABLE_TYPES.map((t) => (
                <option key={t} value={t}>{TYPE_LABEL[t]}</option>
              ))}
            </NativeSelect>
          )}
        </Field>

        {/*
          A soft duplicate guard, not a block. A replacement for a rejected
          document is legitimate and common, so this warns and lets the upload
          proceed; without it a student can queue five ID proofs and staff
          review all five.
        */}
        {pendingSameType ? (
          <p className="text-small text-[var(--ink-700)]">
            You already have {TYPE_LABEL[docType].toLowerCase()} awaiting review. Uploading
            another adds a second one rather than replacing it.
          </p>
        ) : null}

        <FileDropzone
          rule={UPLOAD_RULES.document}
          file={file}
          onSelect={setFile}
          onClear={() => setFile(null)}
          parsing={upload.isPending}
        />

        <Button
          className="self-start"
          disabled={!file}
          loading={upload.isPending}
          onClick={() => { if (file) upload.mutate(file); }}
        >
          Upload
        </Button>
      </section>

      <section aria-labelledby="your-documents">
        <h2 id="your-documents" className="text-h3 mb-[var(--sp-3)] text-[var(--ink-900)]">
          Your documents
        </h2>

        {documents.isPending ? (
          <div className="surface-card p-[var(--sp-6)]"><SkeletonText lines={4} /></div>
        ) : items.length === 0 ? (
          <EmptyState
            icon={FileText}
            heading="No documents yet"
            description="Upload an ID proof or a certificate above and staff will review it."
          />
        ) : (
          <ul className="surface-card divide-y divide-[var(--border)]">
            {items.map((doc) => (
              <li
                key={doc.id}
                className="flex flex-wrap items-center justify-between gap-[var(--sp-3)] p-[var(--sp-4)]"
              >
                <div className="min-w-0">
                  <p className="text-body text-[var(--ink-900)]">{TYPE_LABEL[doc.docType]}</p>
                  <p className="text-caption break-all text-[var(--ink-500)]">{doc.fileName}</p>
                  {/* Neutral badges throughout - verdict colour belongs to the
                      guard screen. The word carries the state. */}
                  <span className="mt-[var(--sp-2)] inline-flex items-center gap-[var(--sp-2)]">
                    <Badge tone="neutral">
                      {doc.verified ? (
                        <>
                          <BadgeCheck className="size-3" aria-hidden />
                          Verified
                        </>
                      ) : doc.verificationRemarks ? (
                        'Rejected'
                      ) : (
                        'Awaiting review'
                      )}
                    </Badge>
                    <span className="text-caption text-[var(--ink-500)]">
                      {formatDateTime(doc.createdAt)}
                    </span>
                  </span>
                  {doc.verificationRemarks && !doc.verified && (
                    <p className="text-caption mt-[var(--sp-2)] max-w-[52ch] text-[var(--ink-700)]">
                      {doc.verificationRemarks}
                    </p>
                  )}
                </div>

                <div className="flex flex-wrap items-center gap-[var(--sp-2)]">
                  {/*
                    Only while AWAITING REVIEW, and only the owner's own - this
                    list is only ever the signed-in student's. A rejected
                    document stays: its remarks are the reviewer's reasoning and
                    the only thing telling the student what to fix. A verified
                    one is evidence and is never deletable, by anyone.
                  */}
                  {isAwaitingReview(doc) ? (
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => setRemoving(doc)}
                    >
                      <Trash2 aria-hidden />Remove
                    </Button>
                  ) : null}

                  {/* A rejection that names what is wrong should lead somewhere. */}
                  {!doc.verified && doc.verificationRemarks ? (
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => {
                        setDocType(doc.docType);
                        window.scrollTo({ top: 0, behavior: 'smooth' });
                      }}
                    >
                      Upload a replacement
                    </Button>
                  ) : null}

                  <Button
                    size="sm"
                    variant="ghost"
                    loading={open.isPending && open.variables === doc.id}
                    onClick={() => openDocument(doc.id)}
                  >
                    <ExternalLink aria-hidden />Open
                  </Button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

      <ConfirmDialog
        open={removing !== null}
        onOpenChange={(next) => { if (!next) setRemoving(null); }}
        title="Remove this document?"
        description={
          removing
            ? `${TYPE_LABEL[removing.docType]} — ${removing.fileName}. The file is deleted and staff will no longer see it. You can upload another.`
            : ''
        }
        confirmLabel="Remove"
        destructive
        loading={remove.isPending}
        onConfirm={() => { if (removing) remove.mutate(removing.id); }}
      />
    </div>
  );
}
