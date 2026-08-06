import { useEffect, useState } from 'react';
import { Download, Mail, QrCode } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { Button } from '@ui/index';
import { qrApi } from '@lib/api/services/qr.api';
import { saveFile } from '@lib/api/download';
import { qrKeys } from '@lib/query/keys';
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
        'relative overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-lg transition-all',
        className
      )}
      aria-label={`${passTypeLabel} ${passCode}`}
    >
      {/* 1. TOP NAVY HEADER BANNER */}
      <div className="bg-[#1c527e] p-5 text-white flex items-center justify-between">
        <div>
          <h2 className="font-extrabold text-base tracking-wide uppercase leading-tight">
            Dr.Dy Patil Pune
          </h2>
          <p className="text-xs text-indigo-100 font-normal">Smart Campus Access</p>
        </div>
        <div className="text-right">
          <div className="font-extrabold text-sm tracking-wider uppercase">{passTypeLabel}</div>
          <div className="font-mono text-xs text-indigo-100">{passCode}</div>
        </div>
      </div>

      {/* 2. HOLDER PROFILE HEADER */}
      <div className="flex items-center gap-4 p-5 pb-4">
        <div className="flex size-14 shrink-0 items-center justify-center rounded-xl bg-slate-100 border border-slate-200 text-slate-400 font-bold text-lg">
          {pass.holderName?.charAt(0) ?? 'S'}
        </div>
        <div className="min-w-0 flex-1">
          <h3 className="font-bold text-lg text-slate-900 truncate leading-snug">{pass.holderName}</h3>
          <p className="text-xs text-slate-500">Show this QR at the gate</p>
        </div>
        <PassStatusBadge status={pass.status} />
      </div>

      <div className="px-5">
        <hr className="border-slate-100" />
      </div>

      {/* 3. 2-COLUMN METADATA GRID */}
      <dl className="grid grid-cols-2 gap-y-4 px-5 py-4 text-xs">
        <div>
          <dt className="font-semibold uppercase tracking-wider text-slate-400 text-[10px]">PASS ID</dt>
          <dd className="font-mono font-bold text-slate-900 text-sm mt-0.5">{passCode}</dd>
        </div>

        <div>
          <dt className="font-semibold uppercase tracking-wider text-slate-400 text-[10px]">TYPE</dt>
          <dd className="font-bold text-slate-900 text-sm mt-0.5">
            {pass.passType === 'EVENT' ? pass.eventName ?? 'Event pass' : 'Daily - standing'}
          </dd>
        </div>

        <div>
          <dt className="font-semibold uppercase tracking-wider text-slate-400 text-[10px]">VALID FROM</dt>
          <dd className="font-semibold text-slate-800 text-xs mt-0.5">
            {pass.validFrom ? new Date(pass.validFrom).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' }) : 'Immediate'}
          </dd>
        </div>

        <div>
          <dt className="font-semibold uppercase tracking-wider text-slate-400 text-[10px]">VALID TO</dt>
          <dd className="font-semibold text-slate-800 text-xs mt-0.5">
            {pass.validTo ? new Date(pass.validTo).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' }) : 'No end date'}
          </dd>
        </div>

        <div>
          <dt className="font-semibold uppercase tracking-wider text-slate-400 text-[10px]">DEPARTMENT</dt>
          <dd className="font-semibold text-slate-800 text-xs mt-0.5">Information technology</dd>
        </div>

        <div>
          <dt className="font-semibold uppercase tracking-wider text-slate-400 text-[10px]">GATE</dt>
          <dd className="font-semibold text-slate-800 text-xs mt-0.5">All campus gates</dd>
        </div>
      </dl>

      {/* 4. QR CODE & ACTIONS SECTION */}
      {detail && (
        <div className="flex flex-col items-center gap-4 border-t border-slate-100 p-6 bg-slate-50/50">
          {pass.scannable && pass.qrKey !== null ? (
            <PassQrImage passId={pass.id} />
          ) : (
            <p className="text-xs text-center text-slate-500">
              {pass.status === 'PENDING'
                ? 'The QR code is currently generating...'
                : 'This pass is not scannable.'}
            </p>
          )}

          <p className="text-center text-[11px] text-slate-500">
            Scan at any gate. Re-issue if your profile changes.
          </p>

          {/* TWO ACTION BUTTONS: Download as PDF & Get on Email */}
          <div className="flex flex-wrap items-center justify-center gap-3 pt-2">
            <Button
              variant="secondary"
              size="sm"
              onClick={() => void handleDownloadPdf()}
              loading={downloadingPdf}
              className="gap-2 border border-slate-300 bg-white hover:bg-slate-50 shadow-2xs"
            >
              <Download className="size-4 text-indigo-600" />
              Download as PDF
            </Button>

            <Button
              variant="secondary"
              size="sm"
              onClick={() => void handleSendEmail()}
              loading={sendingEmail}
              className="gap-2 border border-indigo-200 bg-indigo-50 text-indigo-700 hover:bg-indigo-100 shadow-2xs font-semibold"
            >
              <Mail className="size-4 text-indigo-600" />
              Get on Email
            </Button>
          </div>
        </div>
      )}

      {/* 5. FOOTER */}
      <div className="border-t border-slate-100 bg-slate-50 px-5 py-2.5 text-center text-[10px] text-slate-400 font-mono italic">
        Perimity · entry-only · do not share this code
      </div>
    </article>
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
      <div className="flex size-48 items-center justify-center rounded-xl bg-slate-100 border border-slate-200">
        <QrCode className="size-20 text-slate-400" aria-hidden />
      </div>
    );
  }

  return (
    <div className="flex size-52 items-center justify-center rounded-2xl bg-white p-3 border border-slate-200 shadow-md">
      <img src={src} alt="Pass QR Code" className="size-46 object-contain" />
    </div>
  );
}
