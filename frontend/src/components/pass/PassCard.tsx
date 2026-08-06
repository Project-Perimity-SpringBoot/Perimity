import { useEffect, useState, type ReactNode } from 'react';
import { Download, Mail, QrCode } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { Button } from '@ui/index';
import { qrApi } from '@lib/api/services/qr.api';
import { studentApi } from '@lib/api/services/user.api';
import { saveFile } from '@lib/api/download';
import { AuthedImage } from '@components/upload';
import { profileKeys, qrKeys } from '@lib/query/keys';
import { formatDate } from '@lib/format/datetime';
import { useToast } from '@hooks/useToast';
import { cn } from '@lib/utils/cn';
import type { GatePassResponse } from '@/types/gatepass.types';
import { PassStatusBadge } from './StatusBadge';

import { displayPassCode } from '@lib/format/passCode';

export interface PassCardProps {
  pass: GatePassResponse;
  variant?: 'compact' | 'detail';
  onDownload?: () => void;
  className?: string;
}

/**
 * The gate pass as it appears in the app.
 *
 * Deliberately the same design as the printed PDF and the pass email — violet
 * band, holder block, detail grid, framed QR. A student who compares the three
 * should not have to wonder whether they are looking at the same pass.
 *
 * NOTHING CAMPUS-SPECIFIC IS WRITTEN HERE. The header used to carry a real
 * college name and the grid a hardcoded "Information technology", neither of
 * which is on GatePassResponse — so every pass in the system claimed the same
 * institution and the same department regardless of who held it. Fields the
 * API does not return are not shown at all.
 */
export function PassCard({ pass, variant = 'compact', onDownload, className }: PassCardProps) {
  const toast = useToast();
  const detail = variant === 'detail';
  const passCode = displayPassCode(pass);
  const passTypeLabel = pass.passType === 'EVENT' ? 'EVENT PASS' : 'DAILY PASS';

  const [downloadingPdf, setDownloadingPdf] = useState(false);
  const [sendingEmail, setSendingEmail] = useState(false);

  const handleDownloadPdf = async () => {
    if (onDownload) {
      onDownload();
      return;
    }
    try {
      setDownloadingPdf(true);
      const file = await qrApi.pdf(pass.id);
      saveFile(file);
      toast.success('Pass PDF downloaded successfully');
    } catch {
      toast.error('Could not download PDF pass', 'Please try again later.');
    } finally {
      setDownloadingPdf(false);
    }
  };

  const handleSendEmail = async () => {
    try {
      setSendingEmail(true);
      const result = await qrApi.sendEmail(pass.id);
      toast.success('Pass emailed successfully', `Sent to ${result.email}`);
    } catch {
      toast.error('Could not send email', 'Please try again later.');
    } finally {
      setSendingEmail(false);
    }
  };

  return (
    <article
      className={cn(
        'relative overflow-hidden rounded-2xl border border-violet-100 bg-white shadow-lg shadow-violet-900/5 transition-all',
        className
      )}
      aria-label={`${passTypeLabel} ${passCode}`}
    >
      {/* 1. VIOLET HEADER BAND */}
      <div className="flex items-center justify-between bg-gradient-to-r from-violet-900 to-violet-700 p-5 text-white">
        <div className="min-w-0">
          <h2 className="text-base leading-tight font-extrabold tracking-wide uppercase">
            Gate pass
          </h2>
          <p className="text-xs font-normal text-violet-200">Smart Campus Access</p>
        </div>
        <div className="shrink-0 text-right">
          <div className="text-sm font-extrabold tracking-wider uppercase">{passTypeLabel}</div>
          <div className="font-mono text-xs text-violet-200">{passCode}</div>
        </div>
      </div>

      {/* 2. HOLDER */}
      <div className="flex items-center gap-4 p-5 pb-4">
        <HolderPhoto userId={pass.holderUserId} name={pass.holderName} />
        <div className="min-w-0 flex-1">
          <h3 className="truncate text-lg leading-snug font-bold text-indigo-950">
            {pass.holderName}
          </h3>
          <p className="text-xs text-slate-500">Show this QR at the gate</p>
        </div>
        <PassStatusBadge status={pass.status} />
      </div>

      <div className="px-5">
        <hr className="border-violet-100" />
      </div>

      {/* 3. DETAIL GRID */}
      <dl className="grid grid-cols-2 gap-y-4 px-5 py-4 text-xs">
        <Field label="Pass ID">
          <span className="font-mono">{passCode}</span>
        </Field>

        <Field label="Type">
          {pass.passType === 'EVENT' ? (pass.eventName ?? 'Event pass') : 'Daily - standing'}
        </Field>

        <Field label="Valid from">
          {/*
            formatDate, not new Date(). A pass validity is a LocalDate with no
            zone, and new Date('2026-08-14') parses it as UTC midnight - so in
            any negative-offset zone the card shows the day before the pass is
            actually valid from. A guard reading that turns someone away.
          */}
          {pass.validFrom ? formatDate(pass.validFrom) : 'Immediate'}
        </Field>

        <Field label="Valid to">{pass.validTo ? formatDate(pass.validTo) : 'No end date'}</Field>

        <Field label="Issued">{pass.createdAt ? formatDate(pass.createdAt) : '—'}</Field>

        <Field label="Gate">All campus gates</Field>
      </dl>

      {/* 4. QR AND ACTIONS */}
      {detail && (
        <div className="flex flex-col items-center gap-4 border-t border-violet-100 bg-violet-50/60 p-6">
          {pass.scannable && pass.qrKey !== null ? (
            <PassQrImage passId={pass.id} />
          ) : (
            <p className="text-center text-xs text-slate-500">
              {pass.status === 'PENDING'
                ? 'The QR code is currently generating...'
                : 'This pass is not scannable.'}
            </p>
          )}

          <p className="text-center text-[11px] text-slate-500">
            Scan at any gate. Re-issue if your profile changes.
          </p>

          <div className="flex flex-wrap items-center justify-center gap-3 pt-2">
            <Button
              variant="secondary"
              size="sm"
              onClick={() => void handleDownloadPdf()}
              loading={downloadingPdf}
              className="gap-2 border border-violet-200 bg-white shadow-2xs hover:bg-violet-50"
            >
              <Download className="size-4 text-violet-700" />
              Download as PDF
            </Button>

            <Button
              variant="secondary"
              size="sm"
              onClick={() => void handleSendEmail()}
              loading={sendingEmail}
              className="gap-2 border border-violet-200 bg-violet-100 font-semibold text-violet-800 shadow-2xs hover:bg-violet-200"
            >
              <Mail className="size-4 text-violet-700" />
              Get on Email
            </Button>
          </div>
        </div>
      )}

      {/* 5. FOOTER */}
      <div className="border-t border-violet-100 bg-violet-50 px-5 py-2.5 text-center font-mono text-[10px] text-violet-900/70">
        Perimity · entry-only · do not share this code
      </div>
    </article>
  );
}

/**
 * The holder's face, falling back to their initial.
 *
 * ==========================================================================
 * WHY THE CARD FETCHES THIS ITSELF
 * ==========================================================================
 * GatePassResponse carries holderUserId and holderName and no photo. Adding
 * one would mean a change to a contract that gatepass-service, qr-service and
 * guard-service all read, so the card resolves it here instead: one call to
 * the student profile the pass belongs to, which already returns photoUrl.
 *
 * ==========================================================================
 * A MISSING PHOTO IS NORMAL, NOT AN ERROR
 * ==========================================================================
 * Three ordinary cases end with no image and none of them is a fault:
 *   - the holder is a VISITOR, who has no student profile at all, so the
 *     lookup 404s
 *   - an imported student whose photo could not be read from Drive
 *   - a student who has not uploaded one yet
 *
 * So retry is off - retrying a 404 three times per card is just latency - and
 * every failure lands on the initial. AuthedImage handles the other half: the
 * local storage URL sits behind the JWT filter and an <img> sends no
 * Authorization header, so the bytes have to be fetched and turned into a
 * blob.
 */
function HolderPhoto({ userId, name }: { userId: number; name: string }) {
  const profile = useQuery({
    queryKey: profileKeys.studentByUser(userId),
    queryFn: () => studentApi.byUser(userId),
    retry: false,
    staleTime: 5 * 60 * 1000,
  });

  const profileId = profile.data?.id;
  const hasPhoto = profile.data?.photoS3Key != null;

  /*
   * Two calls, and the second is gated on the first having found a photo.
   *
   * StudentProfileResponse carries photoS3Key - a storage key, not a URL -
   * because a permanent URL to somebody's photograph is not a thing to put in
   * an ordinary API response. /photo-url mints a short-lived link instead.
   *
   * `enabled` matters more than it looks: without it, every pass belonging to
   * a student with no photo would still ask for a link to one, which is a
   * round trip per card to be told nothing exists.
   */
  const photo = useQuery({
    queryKey: profileKeys.photoUrl('student', profileId ?? 0),
    queryFn: () => studentApi.photoUrl(profileId as number),
    enabled: profileId != null && hasPhoto,
    retry: false,
    staleTime: 5 * 60 * 1000,
  });

  const initial = (
    <span className="text-lg font-bold text-violet-700">
      {name?.charAt(0).toUpperCase() ?? '?'}
    </span>
  );

  return (
    <div className="flex size-14 shrink-0 items-center justify-center overflow-hidden rounded-xl bg-violet-100">
      <AuthedImage
        url={photo.data?.url}
        alt={`Photo of ${name}`}
        className="size-full object-cover"
        fallback={initial}
      />
    </div>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt className="text-[10px] font-semibold tracking-wider text-slate-400 uppercase">{label}</dt>
      <dd className="mt-0.5 text-sm font-semibold text-indigo-950">{children}</dd>
    </div>
  );
}

function PassQrImage({ passId }: { passId: number }) {
  const [src, setSrc] = useState<string | null>(null);

  const qrQuery = useQuery({
    queryKey: qrKeys.byPass(passId),
    queryFn: () => qrApi.image(passId),
    retry: 1,
  });

  useEffect(() => {
    if (!qrQuery.data) return undefined;
    const url = URL.createObjectURL(qrQuery.data.blob);
    setSrc(url);

    return () => {
      URL.revokeObjectURL(url);
      setSrc(null);
    };
  }, [qrQuery.data]);

  if (qrQuery.isError || !src) {
    return (
      <div className="flex size-48 items-center justify-center rounded-2xl border border-violet-100 bg-white">
        <QrCode className="size-20 text-violet-200" aria-hidden />
      </div>
    );
  }

  return (
    <div className="flex size-52 items-center justify-center rounded-2xl border border-violet-100 bg-white p-3 shadow-md shadow-violet-900/5">
      <img src={src} alt="Pass QR Code" className="size-46 object-contain" />
    </div>
  );
}
