import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { BadgeCheck, ExternalLink, FileText } from 'lucide-react';
import { Badge, Button, Field, NativeSelect, SkeletonText } from '@ui/index';
import { EmptyState, ErrorState } from '@components/feedback';
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
const DOCUMENT_TYPES = Object.keys(TYPE_LABEL) as DocumentType[];

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

  const open = useMutation({
    mutationFn: (id: number) => documentApi.downloadUrl(id),
    onSuccess: (presigned) => window.open(presigned.url, '_blank', 'noopener,noreferrer'),
    onError: (error) => toast.fromError(error, 'That link could not be created.'),
  });

  if (documents.isError) {
    return <ErrorState error={documents.error} onRetry={() => void documents.refetch()} />;
  }

  const items: DocumentResponse[] = documents.data ?? [];

  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      <PageHeader
        title="Documents"
        description="Upload an ID or certificate for staff to verify."
      />

      <section className="surface-card flex flex-col gap-[var(--sp-4)] p-[var(--sp-6)]">
        <h2 className="text-h3 text-[var(--ink-900)]">Upload a document</h2>

        <Field label="What is it?">
          {({ id }) => (
            <NativeSelect
              id={id}
              className="max-w-64"
              value={docType}
              onChange={(e) => setDocType(e.target.value as DocumentType)}
            >
              {DOCUMENT_TYPES.map((t) => (
                <option key={t} value={t}>{TYPE_LABEL[t]}</option>
              ))}
            </NativeSelect>
          )}
        </Field>

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

                <Button
                  size="sm"
                  variant="ghost"
                  loading={open.isPending && open.variables === doc.id}
                  onClick={() => open.mutate(doc.id)}
                >
                  <ExternalLink aria-hidden />Open
                </Button>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
