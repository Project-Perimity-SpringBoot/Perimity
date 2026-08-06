import { useEffect, useState } from 'react';
import { CalendarDays, Download, QrCode, ShieldCheck } from 'lucide-react';
import { Badge, Button } from '@ui/index';
import { qrApi } from '@lib/api/services/qr.api';
import { qrKeys } from '@lib/query/keys';
import { flags } from '@lib/config';
import { qrApi } from '@lib/api/services/qr.api';
import { formatValidity } from '@lib/format/datetime';
import { displayPassCode } from '@lib/format/passCode';
import { cn } from '@lib/utils/cn';
import type { GatePassResponse } from '@/types/gatepass.types';
import { PassStatusBadge } from './StatusBadge';

type Ribbon = 'daily' | 'event' | 'visitor';

function ribbonFor(pass: GatePassResponse): Ribbon {
  if (pass.passType === 'EVENT') return 'event';
  return pass.visitorRequestId !== null ? 'visitor' : 'daily';
}

const RIBBON_COLOR: Record<Ribbon, string> = {
  daily: 'bg-[var(--pass-daily)]',
  event: 'bg-[var(--pass-event)]',
  visitor: 'bg-[var(--pass-visitor)]',
};

const RIBBON_LABEL: Record<Ribbon, string> = {
  daily: 'Daily pass',
  event: 'Event pass',
  visitor: 'Visitor pass',
};

export interface PassCardProps {
  pass: GatePassResponse;
  variant?: 'compact' | 'detail';
  onDownload?: () => void;
  className?: string;
}

export function PassCard({ pass, variant = 'compact', onDownload, className }: PassCardProps) {
  const ribbon = ribbonFor(pass);
  const detail = variant === 'detail';

  /*
   * THE REAL QR, NOT A GLYPH.
   *
   * This box used to draw a grey square with a QR icon in it — a stand-in from
   * when nothing served the PNG. It cannot be scanned, and next to a genuine QR
   * it reads as one that failed to load.
   *
   * Fetched here rather than passed in as a prop. The visitor pass page already
   * runs this exact query under the same key, so React Query serves both from
   * one request and one cache entry — no second network call, and no prop to
   * thread through every page that renders a detail card. The student pass
   * detail page, which has no QR panel at all, gets a real QR from this for the
   * first time.
   *
   * Only for the detail variant: the dashboards render compact cards in lists,
   * and fetching an image per row is not worth it for something they do not show.
   */
  const scannable = pass.scannable && pass.qrKey !== null;
  const wantsQr = detail && scannable && flags.passDownload;

  const [qrSrc, setQrSrc] = useState<string | null>(null);
  const qr = useQuery({
    queryKey: qrKeys.byPass(pass.id),
    queryFn: () => qrApi.image(pass.id),
    enabled: wantsQr,
    retry: 1,
  });

  useEffect(() => {
    if (!qr.data) return undefined;
    const url = URL.createObjectURL(qr.data.blob);
    setQrSrc(url);
    // Without this every re-render leaks a blob for the lifetime of the tab.
    return () => {
      URL.revokeObjectURL(url);
      setQrSrc(null);
    };
  }, [qr.data]);

  return (
    <article
      className={cn('surface-card relative overflow-hidden', className)}
      aria-label={`${RIBBON_LABEL[ribbon]} ${displayPassCode(pass)}`}
    >
      <span aria-hidden className={cn('absolute inset-x-0 top-0 h-1', RIBBON_COLOR[ribbon])} />

      <div className="flex items-start justify-between gap-[var(--sp-4)] p-[var(--sp-4)] pt-[var(--sp-6)]">
        <div className="min-w-0">
          <Badge tone={ribbon}>{RIBBON_LABEL[ribbon]}</Badge>
          <h3 className="text-h3 mt-[var(--sp-2)] truncate text-[var(--ink-900)]">{pass.holderName}</h3>
          <p className="text-mono mt-[var(--sp-1)] text-[var(--ink-700)]">{displayPassCode(pass)}</p>
        </div>
        <PassStatusBadge status={pass.status} />
      </div>

      <dl className="grid gap-[var(--sp-2)] px-[var(--sp-4)] pb-[var(--sp-4)] text-small">
        {pass.eventName && (
          <div className="flex items-center gap-[var(--sp-2)] text-[var(--ink-700)]">
            <CalendarDays className="size-4 text-[var(--ink-400)]" aria-hidden />
            <dt className="sr-only">Event</dt>
            <dd className="truncate">{pass.eventName}</dd>
          </div>
        )}
        <div className="flex items-center gap-[var(--sp-2)] text-[var(--ink-700)]">
          <ShieldCheck className="size-4 text-[var(--ink-400)]" aria-hidden />
          <dt className="sr-only">Validity</dt>
          <dd>{formatValidity(pass.validFrom, pass.validTo)}</dd>
        </div>
        {pass.status === 'PAUSED' && pass.pausedReason && (
          <div className="rounded-[var(--r-sm)] bg-[var(--surface-sunken)] p-[var(--sp-2)] text-[var(--ink-700)]">
            <dt className="text-label text-[var(--ink-500)]">Why this is paused</dt>
            <dd className="mt-[2px]">{pass.pausedReason}</dd>
          </div>
        )}
        {pass.status === 'REVOKED' && pass.revokedReason && (
          <div className="rounded-[var(--r-sm)] bg-[var(--surface-sunken)] p-[var(--sp-2)] text-[var(--ink-700)]">
            <dt className="text-label text-[var(--ink-500)]">Why this was revoked</dt>
            <dd className="mt-[2px]">{pass.revokedReason}</dd>
          </div>
        )}
      </dl>

      {detail && (
        <div className="flex flex-col items-center gap-[var(--sp-2)] border-t border-[var(--border)] p-[var(--sp-6)]">
          {pass.scannable ? (
            <PassQrImage passId={pass.id} />
          ) : (
            <p className="text-small text-center text-[var(--ink-500)]">
              {pass.status === 'PENDING'
                ? 'The QR code is still being generated. This page updates itself.'
                : 'This pass is not scannable, so no QR is shown.'}
            </p>
          )}

          {flags.passDownload && onDownload && (
            <Button variant="secondary" onClick={onDownload}>
              <Download aria-hidden />
              Download PDF
            </Button>
          )}
        </div>
      )}
    </article>
  );
}

function PassQrImage({ passId }: { passId: number }) {
  const [src, setSrc] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let objectUrl: string | null = null;
    let cancelled = false;

    void (async () => {
      try {
        const file = await qrApi.image(passId);
        if (cancelled) return;
        objectUrl = URL.createObjectURL(file.blob);
        setSrc(objectUrl);
        setFailed(false);
      } catch {
        if (!cancelled) setFailed(true);
      }
    })();

    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [passId]);

  if (failed || !src) {
    return (
      <div className="flex size-44 items-center justify-center rounded-[var(--r-md)] bg-[var(--surface-sunken)]">
        <QrCode className="size-16 text-[var(--ink-400)]" aria-hidden />
      </div>
    );
  }

  return (
    <div className="flex size-44 items-center justify-center rounded-[var(--r-md)] bg-white p-2 border border-[var(--border)] shadow-sm">
      <img src={src} alt="Pass QR Code" className="size-40 object-contain" />
    </div>
  );
}
