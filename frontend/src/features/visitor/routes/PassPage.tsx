import { useEffect, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { Download, QrCode, RefreshCw } from 'lucide-react';
import { Button, Skeleton, SkeletonText } from '@ui/index';
import { PageHeader } from '@components/data';
import { EmptyState, ErrorState } from '@components/feedback';
import { PassCard } from '@components/pass';
import { passApi } from '@lib/api/services/gatepass.api';
import { qrApi } from '@lib/api/services/qr.api';
import { passKeys, qrKeys } from '@lib/query/keys';
import { saveFile } from '@lib/api/download';
import { flags } from '@lib/config';
import { useToast } from '@hooks/useToast';
import type { GatePassResponse } from '@/types/gatepass.types';

/**
 * Phase 5 screens 5 and 6 — the pass, and the event pass.
 *
 * One screen, because they are the same object with different copy. An event
 * pass differs in its ribbon, its dates and one sentence; two files would mean
 * two places to fix the day the QR rendering changes.
 *
 * ==========================================================================
 * LOADING AND ERROR STATES ARE REQUIRED HERE, NOT OPTIONAL
 * ==========================================================================
 * This is the screen a visitor opens standing at a gate, on guest wifi, with
 * somebody waiting behind them. A failing request is the realistic case, not
 * the edge case, and a blank screen at that moment is a product failure — the
 * visitor cannot tell whether their pass is invalid or the page simply has not
 * loaded, and neither can the guard looking over their shoulder.
 *
 * So: a skeleton while loading, an explicit error with a retry that says what
 * to do, and the pass details rendered even when the QR image itself fails.
 * Somebody who can read their pass code aloud can still be let in manually.
 */
export default function PassPage() {
  const passes = useQuery({
    queryKey: passKeys.mine(),
    queryFn: () => passApi.mine(),
  });

  if (passes.isError) {
    return (
      <ErrorState
        error={passes.error}
        onRetry={() => void passes.refetch()}
      />
    );
  }

  if (passes.isPending) {
    return (
      <div className="flex flex-col gap-[var(--sp-6)]">
        <PageHeader title="Your pass" />
        <div className="surface-card flex flex-col gap-[var(--sp-4)] p-[var(--sp-6)]">
          <SkeletonText lines={4} />
          <Skeleton className="mx-auto size-56" />
        </div>
      </div>
    );
  }

  const usable = (passes.data ?? []).filter(
    (p) => p.status === 'ACTIVE' || p.status === 'PENDING',
  );
  const pass = usable.find((p) => p.passType === 'EVENT') ?? usable[0];

  if (!pass) {
    return (
      <div className="flex flex-col gap-[var(--sp-6)]">
        <PageHeader title="Your pass" />
        <EmptyState
          icon={QrCode}
          heading="You have no pass to show yet"
          description="Once your host approves a request, your pass and its QR appear here and arrive by email."
          action={
            <Button asChild>
              <Link to="/visitor">Back to your dashboard</Link>
            </Button>
          }
        />
      </div>
    );
  }

  const isEvent = pass.passType === 'EVENT';
  const alsoHasDaily = isEvent && usable.some((p) => p.passType === 'DAILY');

  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      <PageHeader
        title={isEvent && pass.eventName ? pass.eventName : 'Your pass'}
        description={
          isEvent
            ? 'Use this QR for the programme.'
            : 'Show this QR at the gate. A copy was emailed to you.'
        }
      />

      <PassCard pass={pass} variant="detail" />

      <QrPanel pass={pass} />

      {alsoHasDaily ? (
        <p className="text-caption text-[var(--ink-500)]">
          You also hold a daily pass. Either QR works, and your entry is recorded
          against the event.
        </p>
      ) : null}
    </div>
  );
}

/**
 * The QR itself, and the PDF download.
 *
 * The image is fetched rather than pointed at with an <img src>. The endpoint
 * requires a Bearer token and an image tag sends no Authorization header, so a
 * naive src would 401 every time. The bytes come back through the API layer and
 * are rendered from an object URL, which is also revoked on unmount — without
 * that, every visit leaks a blob for the life of the tab.
 */
function QrPanel({ pass }: { pass: GatePassResponse }) {
  const toast = useToast();
  const [objectUrl, setObjectUrl] = useState<string | null>(null);

  const image = useQuery({
    queryKey: qrKeys.byPass(pass.id),
    queryFn: () => qrApi.image(pass.id),
    enabled: flags.passDownload,
    retry: 1,
  });

  useEffect(() => {
    if (!image.data) return;
    const url = URL.createObjectURL(image.data.blob);
    setObjectUrl(url);
    return () => {
      URL.revokeObjectURL(url);
      setObjectUrl(null);
    };
  }, [image.data]);

  const pdf = useMutation({
    mutationFn: () => qrApi.pdf(pass.id),
    onSuccess: saveFile,
    onError: (error) => toast.fromError(error, 'That pass could not be downloaded.'),
  });

  /*
   * B1 is closed and the flag now defaults on, so this is the kill-switch path
   * rather than the normal one. Kept because it is the honest fallback: an
   * explanation rather than a broken image, with the pass details above still
   * readable and actionable at a gate.
   */
  if (!flags.passDownload) {
    return (
      <section className="surface-card flex flex-col items-center gap-[var(--sp-3)] p-[var(--sp-6)] text-center">
        <QrCode className="size-8 text-[var(--ink-500)]" aria-hidden />
        <p className="text-body-md text-[var(--ink-900)]">
          Your QR is in the email we sent you
        </p>
        <p className="text-caption max-w-[46ch] text-[var(--ink-500)]">
          Showing it here is not switched on yet. The PDF attached to your pass email
          has the same QR and works at every gate.
        </p>
      </section>
    );
  }

  return (
    <section className="surface-card flex flex-col items-center gap-[var(--sp-4)] p-[var(--sp-6)]">
      {image.isPending ? (
        <>
          <Skeleton className="size-56" />
          <p className="text-caption text-[var(--ink-500)]">Loading your QR…</p>
        </>
      ) : image.isError ? (
        /*
         * The realistic failure. Says what the visitor can do RIGHT NOW at the
         * gate — the pass details above are still on screen and still valid —
         * rather than only offering a retry that may fail again on the same
         * wifi.
         */
        <div className="flex max-w-[46ch] flex-col items-center gap-[var(--sp-3)] text-center">
          <QrCode className="size-8 text-[var(--ink-500)]" aria-hidden />
          <p className="text-body-md text-[var(--ink-900)]">Your QR did not load</p>
          <p className="text-caption text-[var(--ink-500)]">
            Your pass is still valid — the details above are correct. Show the PDF from
            your email, or ask the guard to look you up by the code on your pass.
          </p>
          <Button variant="secondary" onClick={() => void image.refetch()}>
            <RefreshCw aria-hidden />Try again
          </Button>
        </div>
      ) : objectUrl ? (
        <img
          src={objectUrl}
          alt={`QR code for pass ${pass.id}`}
          className="size-56 rounded-[var(--r-md)]"
        />
      ) : null}

      <Button onClick={() => pdf.mutate()} loading={pdf.isPending}>
        <Download aria-hidden />Download PDF
      </Button>
    </section>
  );
}
