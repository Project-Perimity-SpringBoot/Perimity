import { CalendarDays, Download, QrCode, ShieldCheck } from 'lucide-react';
import { Badge, Button } from '@ui/index';
import { flags } from '@lib/config';
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
          {pass.scannable && pass.qrKey ? (
            <div className="flex size-40 items-center justify-center rounded-[var(--r-md)] bg-[var(--surface-sunken)]">
              <QrCode className="size-16 text-[var(--ink-400)]" aria-hidden />
            </div>
          ) : (
            <p className="text-small text-center text-[var(--ink-500)]">
              {pass.status === 'PENDING'
                ? 'The QR code is still being generated. This page updates itself.'
                : 'This pass is not scannable, so no QR is shown.'}
            </p>
          )}

          {/* Nothing in qr-service serves the PNG or the PDF yet, so the action
              is hidden behind a flag rather than shipped as a broken button. */}
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
