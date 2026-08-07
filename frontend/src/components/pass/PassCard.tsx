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
 *
 * ==========================================================================
 * WHY THIS ONE FILE KEEPS ITS OWN PALETTE
 * ==========================================================================
 * Every other surface in the product moved onto the shared surface/ink tokens.
 * This one did not, and the violet is unchanged to the hex: the same pass is
 * also rendered by qr-service as a PDF and as an emailed image, and a card
 * that drifted to the app's indigo would no longer match the thing in the
 * student's inbox. What DID change is that the values now live in tokens.css
 * as --passcard-*, so the three renderings have one place to be reconciled
 * instead of a colour spelled out in twenty class names here.
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
        'relative overflow-hidden rounded-[var(--r-lg)] border border-[var(--passcard-hairline)]',
        'bg-white shadow-[var(--passcard-shadow)]',
        className
      )}
      aria-label={`${passTypeLabel} ${passCode}`}
    >
      {/* 1. VIOLET HEADER BAND */}
      <div className="flex items-center justify-between bg-[linear-gradient(to_right,var(--passcard-band-from),var(--passcard-band-to))] p-[var(--sp-4)] text-white">
        <div className="min-w-0">
          <h2 className="text-h3 uppercase">Gate pass</h2>
          <p className="text-caption text-[var(--passcard-band-ink)]">Smart Campus Access</p>
        </div>
        <div className="shrink-0 text-right">
          <div className="text-label">{passTypeLabel}</div>
          <div className="text-mono text-[var(--passcard-band-ink)]">{passCode}</div>
        </div>
      </div>

      {/* 2. HOLDER */}
      <div className="flex items-center gap-[var(--sp-4)] p-[var(--sp-4)]">
        <HolderPhoto userId={pass.holderUserId} name={pass.holderName} />
        <div className="min-w-0 flex-1">
          <h3 className="text-h2 truncate text-[var(--passcard-ink)]">{pass.holderName}</h3>
          <p className="text-caption text-[var(--passcard-ink-soft)]">Show this QR at the gate</p>
        </div>
        <PassStatusBadge status={pass.status} />
      </div>

      <div className="px-[var(--sp-4)]">
        <hr className="border-[var(--passcard-hairline)]" />
      </div>

      {/* 3. DETAIL GRID */}
      <dl className="grid grid-cols-2 gap-y-[var(--sp-4)] px-[var(--sp-4)] py-[var(--sp-4)]">
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
        <div className="flex flex-col items-center gap-[var(--sp-4)] border-t border-[var(--passcard-hairline)] bg-[var(--passcard-wash)] p-[var(--sp-6)]">
          {pass.scannable && pass.qrKey !== null ? (
            <PassQrImage passId={pass.id} />
          ) : (
            <p className="text-caption text-center text-[var(--passcard-ink-soft)]">
              {pass.status === 'PENDING'
                ? 'The QR code is currently generating…'
                : 'This pass is not scannable.'}
            </p>
          )}

          <p className="text-caption text-center text-[var(--passcard-ink-soft)]">
            Scan at any gate. Re-issue if your profile changes.
          </p>

          <div className="flex flex-wrap items-center justify-center gap-[var(--sp-2)]">
            <Button
              variant="secondary"
              size="sm"
              onClick={() => void handleDownloadPdf()}
              loading={downloadingPdf}
              className="border-[var(--passcard-edge)] hover:bg-[var(--passcard-wash)]"
            >
              <Download className="text-[var(--passcard-accent)]" aria-hidden />
              Download as PDF
            </Button>

            <Button
              variant="secondary"
              size="sm"
              onClick={() => void handleSendEmail()}
              loading={sendingEmail}
              className="border-[var(--passcard-edge)] bg-[var(--passcard-tint)] text-[var(--passcard-accent-strong)] hover:bg-[var(--passcard-edge)]"
            >
              <Mail className="text-[var(--passcard-accent)]" aria-hidden />
              Get on Email
            </Button>
          </div>
        </div>
      )}

      {/* 5. FOOTER */}
      <div className="text-mono border-t border-[var(--passcard-hairline)] bg-[var(--passcard-wash)] px-[var(--sp-4)] py-[var(--sp-2)] text-center text-[var(--passcard-footer-ink)]">
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
    <span className="text-h2 text-[var(--passcard-accent)]">
      {name?.charAt(0).toUpperCase() ?? '?'}
    </span>
  );

  return (
    <div className="flex size-14 shrink-0 items-center justify-center overflow-hidden rounded-[var(--r-md)] bg-[var(--passcard-tint)]">
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
      <dt className="text-label text-[var(--passcard-ink-faint)]">{label}</dt>
      <dd className="text-small mt-[var(--sp-1)] text-[var(--passcard-ink)]">{children}</dd>
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
      <div className="flex size-48 items-center justify-center rounded-[var(--r-lg)] border border-[var(--passcard-hairline)] bg-white">
        <QrCode className="size-20 text-[var(--passcard-edge)]" aria-hidden />
      </div>
    );
  }

  return (
    <div className="flex size-52 items-center justify-center rounded-[var(--r-lg)] border border-[var(--passcard-hairline)] bg-white p-[var(--sp-3)] shadow-[var(--passcard-shadow)]">
      <img src={src} alt="Pass QR Code" className="size-46 object-contain" />
    </div>
  );
}
