import { useCallback, useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router';
import {
  Camera, CameraOff, Keyboard, Monitor, ScanLine, ShieldCheck, WifiOff,
} from 'lucide-react';
import { Button, Field, Input, Skeleton } from '@ui/index';
import { ErrorState } from '@components/feedback';
import { scanApi, entryLogApi, sessionApi } from '@lib/api/services/guard.api';
import { guardKeys } from '@lib/query/keys';
import { formatTime } from '@lib/format/datetime';
import { flags } from '@lib/config';
import { useMediaQuery } from '@hooks/useMediaQuery';
import { useToast } from '@hooks/useToast';
import type { ScanResponse } from '@/types/guard.types';
import { VerdictScreen } from '../components/VerdictScreen';
import { useQrScanner } from '../components/useQrScanner';

/**
 * Phase 6 screen 1 — the scanner. This is the guard's home screen.
 *
 * ==========================================================================
 * THERE IS STILL NO GUARD DASHBOARD, AND THAT IS STILL THE DESIGN
 * ==========================================================================
 * A guard opens this app for exactly one reason. Shift counters, recent
 * activity and gate details would compete with the viewfinder for the one
 * screen that matters, so they live on the end-shift summary instead. What
 * changed is presentation: the camera and the typed code are now two named
 * modes rather than one path and a disclosure, because on a busy morning a
 * guard should not have to discover the fallback.
 *
 * ==========================================================================
 * WHY THE MODE TOGGLE LIVES HERE AND NOT IN THE HEADER
 * ==========================================================================
 * It reads like header chrome, but the state belongs to this screen. Lifting
 * it into GuardShell would mean the shell knowing which of its children cares
 * about a mode, and every other guard route ignoring a control that is still
 * on their screen. One component owns it; the header stays what it is, which
 * is the answer to "which gate am I on".
 *
 * ==========================================================================
 * A DENIAL IS NOT AN HTTP ERROR
 * ==========================================================================
 * The server returns 200 for every decided scan — ALLOWED, AMBER and DENIED
 * alike. A refusal is an answer, not a failure, so it renders as a verdict.
 * Only a missing shift (400), the wrong role (403) or an unreachable
 * verification hop (503) reach the error path.
 *
 * That 503 is worth knowing about: guard-service fails CLOSED when it cannot
 * verify a pass, and it fails fast rather than hanging — the circuit breaker
 * refuses immediately instead of waiting out a timeout per scan. Either way
 * the guard is told the scanner is down, not that the person is refused.
 *
 * ==========================================================================
 * FIRE AND SHOW
 * ==========================================================================
 * Decode, POST, render the verdict. There is no confirmation tap in between,
 * because the queue is the thing this screen fights and the verdict is its own
 * confirmation.
 */

type Mode = 'camera' | 'manual';

export default function ScannerPage() {
  const toast = useToast();
  const queryClient = useQueryClient();

  const [verdict, setVerdict] = useState<ScanResponse | null>(null);
  const [typed, setTyped] = useState('');
  const [mode, setMode] = useState<Mode>('camera');
  const [online, setOnline] = useState(() => navigator.onLine);

  // Phones only for the camera. A guard post is a phone; a desktop here is
  // someone at a supervisor's machine, and they get the typed path instead of
  // a viewfinder they cannot point at anything.
  const isDesktop = useMediaQuery('(min-width: 1024px)');

  const session = useQuery({
    queryKey: guardKeys.currentSession(),
    queryFn: () => sessionApi.current(),
    retry: false,
  });

  const scan = useMutation({
    mutationFn: (token: string) =>
      scanApi.scan({
        token,
        // No gateId, campusId or scannedAt. They come from the open session and
        // the server clock - if the client supplied them, the entry log would
        // be a claim rather than evidence.
        deviceInfo: { userAgent: navigator.userAgent.slice(0, 60), appVersion: 'web' },
      }),
    onSuccess: (result) => {
      setVerdict(result);
      setTyped('');
      // The shift counters on the end-shift summary come off the session
      // record, so it is now one scan out of date.
      void queryClient.invalidateQueries({ queryKey: guardKeys.currentSession() });
      void queryClient.invalidateQueries({ queryKey: guardKeys.entryLogs() });
    },
    onError: (error) => toast.fromError(error, 'That scan could not be completed.'),
  });

  const onToken = useCallback((token: string) => {
    // Ignore decodes while a verdict is on screen or a request is in flight -
    // the camera keeps running behind the card and would otherwise queue scans
    // the guard never asked for.
    if (verdict !== null || scan.isPending) return;
    scan.mutate(token);
  }, [verdict, scan]);

  const camera = useQrScanner(onToken);

  useEffect(() => {
    const up = () => setOnline(true);
    const down = () => setOnline(false);
    window.addEventListener('online', up);
    window.addEventListener('offline', down);
    return () => {
      window.removeEventListener('online', up);
      window.removeEventListener('offline', down);
    };
  }, []);

  /*
   * TWO DIFFERENT THINGS, AND CONFLATING THEM WAS A BUG.
   *
   * `cameraImpossible` means the camera cannot work at all: no BarcodeDetector
   * in this browser, permission refused, or getUserMedia failed. Only this
   * disables the Camera tab, and when it does the reason is printed under the
   * tabs — a control that does nothing when clicked, with no explanation, is
   * worse than no control.
   *
   * `isDesktop` means something much weaker: this is probably not a gate phone.
   * It used to disable the tab too, which was wrong. A laptop has a webcam, and
   * a supervisor checking one pass, or anyone demonstrating the system, has
   * every reason to use it. Desktop now gets a note and a button, not a locked
   * door.
   */
  const cameraImpossible =
    camera.state === 'denied' || camera.state === 'unsupported' || camera.state === 'failed';

  const cameraReason =
    camera.state === 'unsupported'
      // Not "this browser cannot decode QR codes" any more — it can, jsQR is
      // loaded on demand for exactly the platforms BarcodeDetector skips.
      // Reaching this state now means that chunk did not load at all.
      ? 'The QR decoder could not be loaded. Check the connection and reload, or use Manual.'
      : camera.state === 'denied'
        ? 'Camera access was refused. Allow it in your browser settings and reload.'
        : camera.state === 'failed'
          ? 'The camera could not be opened. Another app may be using it.'
          : null;

  /*
   * Open on the mode that will work. On a phone that is the camera; on a
   * desktop, or when the camera is impossible, it is the field they are going
   * to type into. Runs once per change of circumstance, not on every render, so
   * it never fights a guard who deliberately switched tabs.
   */
  useEffect(() => {
    if (cameraImpossible || isDesktop) setMode('manual');
  }, [cameraImpossible, isDesktop]);

  const recent = useQuery({
    queryKey: guardKeys.entryLogsBySession(session.data?.id ?? ''),
    queryFn: () => entryLogApi.bySession(session.data?.id as string),
    enabled: session.data?.id != null,
  });

  if (session.isError) {
    return <ErrorState error={session.error} onRetry={() => void session.refetch()} />;
  }

  if (verdict) {
    return <VerdictScreen scan={verdict} onDismiss={() => setVerdict(null)} />;
  }

  const tabClass = (active: boolean) =>
    'flex min-h-14 flex-1 items-center justify-center gap-[var(--sp-2)] rounded-[var(--r-sm)] '
    + 'text-body-md transition-colors duration-[var(--motion-fast)] '
    + (active
      ? 'bg-[var(--surface)] text-[var(--ink-900)] shadow-[var(--sh-card)]'
      : 'text-[var(--ink-500)] hover:text-[var(--ink-900)]');

  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-[var(--sp-6)] p-[var(--sp-4)]">
      {/* Offline. The scan itself needs the network - there is no local
          verification - so this states plainly that scanning will not work
          rather than letting a guard find out one person at a time. */}
      {!online && (
        <p
          role="alert"
          className="flex items-center gap-[var(--sp-2)] rounded-[var(--r-md)]
                     border border-[var(--status-border)] bg-[var(--status-bg)]
                     p-[var(--sp-3)] text-small text-[var(--ink-900)]"
        >
          <WifiOff aria-hidden className="size-4 shrink-0" />
          No connection. Scans cannot be verified until this comes back — do not wave
          people through on the assumption that they would have passed.
        </p>
      )}

      <section className="surface-card overflow-hidden">
        {/* card header */}
        <div className="flex items-center gap-[var(--sp-3)] border-b border-[var(--border)] p-[var(--sp-4)]">
          <span className="flex size-12 shrink-0 items-center justify-center rounded-[var(--r-md)] bg-[var(--brand-600)]">
            {mode === 'camera'
              ? <Camera className="size-6 text-white" aria-hidden />
              : <Keyboard className="size-6 text-white" aria-hidden />}
          </span>
          <div className="min-w-0">
            <h1 className="text-h2 text-[var(--ink-900)]">
              {mode === 'camera' ? 'QR camera scanner' : 'Manual token entry'}
            </h1>
            <p className="text-small text-[var(--ink-500)]">
              {mode === 'camera'
                ? 'Point the camera at a pass to scan automatically'
                : 'Type or paste the code from the pass to check it'}
            </p>
          </div>
        </div>

        {/* mode toggle */}
        <div
          role="tablist"
          aria-label="Scan method"
          className="m-[var(--sp-4)] flex gap-[var(--sp-1)] rounded-[var(--r-md)] bg-[var(--surface-sunken)] p-[var(--sp-1)]"
        >
          <button
            type="button"
            role="tab"
            aria-selected={mode === 'camera'}
            disabled={cameraImpossible}
            title={cameraReason ?? undefined}
            className={tabClass(mode === 'camera') + ' disabled:cursor-not-allowed disabled:opacity-50'}
            onClick={() => setMode('camera')}
          >
            <Camera className="size-5" aria-hidden />
            Camera
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={mode === 'manual'}
            className={tabClass(mode === 'manual')}
            onClick={() => setMode('manual')}
          >
            <Keyboard className="size-5" aria-hidden />
            Manual
          </button>
        </div>

        {cameraReason && (
          <p className="text-small px-[var(--sp-4)] pb-[var(--sp-3)] text-[var(--ink-500)]">
            Camera unavailable — {cameraReason}
          </p>
        )}

        <div className="flex flex-col gap-[var(--sp-4)] p-[var(--sp-4)] pt-0">
          {mode === 'camera' ? (
            <>
              {/* Not a refusal, a heads-up. The gate is a phone; a desktop here
                  is a supervisor's machine or a demo, and a webcam serves both
                  perfectly well. */}
              {isDesktop && camera.state === 'idle' && (
                <div className="flex items-start gap-[var(--sp-3)] rounded-[var(--r-md)] border border-[var(--border)] bg-[var(--surface-subtle)] p-[var(--sp-4)]">
                  <Monitor aria-hidden className="mt-[var(--sp-1)] size-5 shrink-0 text-[var(--ink-500)]" />
                  <p className="text-small text-[var(--ink-700)]">
                    <span className="text-body-md block text-[var(--ink-900)]">
                      This is usually the gate phone&rsquo;s screen
                    </span>
                    The webcam works — Windows has no built-in QR decoding, so this
                    falls back to a JavaScript one. At a real gate use the phone, where
                    the decode is native and free.
                  </p>
                </div>
              )}
              <>
                {/*
                  THE VIDEO IS NEVER UNMOUNTED, ONLY HIDDEN.
                  `camera.videoRef` has to be attached to a real element before
                  start() runs — the hook bails out with `if (!element)` and
                  stops silently otherwise. Rendering the placeholder *instead*
                  of the video would break starting the camera at all.
                */}
                <div
                  className={
                    'relative overflow-hidden rounded-[var(--r-lg)] bg-black '
                    + (camera.state === 'idle' ? 'hidden' : '')
                  }
                >
                  {/* A live viewfinder, not media. No caption track exists or
                      could: there is no audio and no recorded content, and the
                      element is aria-hidden because what matters to a
                      screen-reader user is the verdict, announced with
                      role="alert". */}
                  <video
                    ref={camera.videoRef}
                    aria-hidden
                    className="mx-auto aspect-[3/4] w-full object-cover sm:aspect-[4/3] sm:max-h-[420px] sm:w-auto"
                    playsInline
                    muted
                  />
                  {camera.state === 'running' && (
                    <span
                      aria-hidden
                      className="pointer-events-none absolute inset-[12%] rounded-[var(--r-lg)] border-2 border-white/70"
                    />
                  )}
                </div>

                {/* Before the first tap. A black rectangle the height of the
                    page suggests something is broken; this says what will
                    happen instead. */}
                {camera.state === 'idle' && (
                  <div className="flex flex-col items-center gap-[var(--sp-3)] rounded-[var(--r-lg)] border border-dashed border-[var(--border-strong)] bg-[var(--surface-subtle)] py-[var(--sp-8)] text-center">
                    <span className="flex size-16 items-center justify-center rounded-[var(--r-circle)] bg-[var(--surface-sunken)]">
                      <Camera className="size-8 text-[var(--ink-400)]" aria-hidden />
                    </span>
                    <p className="text-body-md text-[var(--ink-900)]">Camera is off</p>
                    <p className="text-small max-w-[38ch] text-[var(--ink-500)]">
                      Start it below, then hold the pass in front of the lens. Scanning is
                      automatic — there is nothing to press.
                    </p>
                  </div>
                )}

                {camera.state === 'idle' && (
                  <>
                    <p className="text-small rounded-[var(--r-md)] border border-[var(--border)] bg-[var(--surface-subtle)] p-[var(--sp-3)] text-[var(--ink-700)]">
                      Your browser will ask for camera permission. It is used only to read
                      QR codes — nothing is recorded or uploaded.
                    </p>
                    <Button size="lg" block className="min-h-14" onClick={() => void camera.start()}>
                      <Camera aria-hidden />Start camera scan
                    </Button>
                  </>
                )}
                {camera.state === 'starting' && <Skeleton className="h-14 w-full" />}
                {camera.state === 'running' && (
                  <Button size="lg" variant="secondary" block className="min-h-14" onClick={camera.stop}>
                    Pause camera
                  </Button>
                )}

                {camera.state === 'denied' && (
                  <div className="flex flex-col gap-[var(--sp-2)] rounded-[var(--r-md)] border border-[var(--border)] p-[var(--sp-4)]">
                    <p className="text-body-md flex items-center gap-[var(--sp-2)] text-[var(--ink-900)]">
                      <CameraOff aria-hidden className="size-5" />
                      Camera access was refused
                    </p>
                    <p className="text-small text-[var(--ink-700)]">
                      Allow the camera in your browser settings and reload. Until then, use
                      Manual — it works exactly the same way.
                    </p>
                  </div>
                )}

                {(camera.state === 'unsupported' || camera.state === 'failed') && (
                  <div className="flex flex-col gap-[var(--sp-2)] rounded-[var(--r-md)] border border-[var(--border)] p-[var(--sp-4)]">
                    <p className="text-body-md text-[var(--ink-900)]">
                      {camera.state === 'unsupported'
                        ? 'The QR decoder could not be loaded'
                        : 'The camera could not be opened'}
                    </p>
                    <p className="text-small text-[var(--ink-700)]">
                      {camera.state === 'unsupported'
                        ? 'Reload with a working connection, or use Manual — it is not a workaround, it is the same scan.'
                        : 'Another app may be using it. Use Manual instead.'}
                    </p>
                  </div>
                )}
              </>
            </>
          ) : (
            /* ---- typed code: first-class, not a fallback ---- */
            <form
              className="flex flex-col gap-[var(--sp-3)]"
              onSubmit={(e) => {
                e.preventDefault();
                const value = typed.trim();
                if (value) onToken(value);
              }}
            >
              <Field label="Pass code" hint="Read it off the printed or on-screen pass.">
                {({ id, describedBy }) => (
                  <Input
                    id={id}
                    autoComplete="off"
                    autoCapitalize="off"
                    spellCheck={false}
                    placeholder="Scan or paste the code here"
                    aria-describedby={describedBy}
                    className="min-h-14 text-[length:var(--t-h3-size)]"
                    value={typed}
                    onChange={(e) => setTyped(e.target.value)}
                  />
                )}
              </Field>
              <Button
                type="submit"
                size="lg"
                block
                className="min-h-14"
                loading={scan.isPending}
                disabled={!typed.trim()}
              >
                <ShieldCheck aria-hidden />Check this pass
              </Button>
            </form>
          )}
        </div>
      </section>

      {/* Last few scans. Not a dashboard - it is the guard's short-term memory
          for "did I just scan that person", and it stops at five deliberately. */}
      {(recent.data?.length ?? 0) > 0 && (
        <section aria-labelledby="recent">
          <h2 id="recent" className="text-small mb-[var(--sp-2)] text-[var(--ink-500)]">
            This shift, most recent first
          </h2>
          <ul className="surface-card divide-y divide-[var(--border)]">
            {(recent.data ?? []).slice(-5).reverse().map((entry) => (
              <li key={entry.id} className="flex items-baseline justify-between gap-[var(--sp-3)] p-[var(--sp-3)]">
                <span className="truncate text-small text-[var(--ink-900)]">
                  {entry.holderName ?? 'Unknown pass'}
                </span>
                <span className="text-caption shrink-0 text-[var(--ink-500)]">
                  {entry.scanResult === 'DENIED' ? 'Refused' : 'In'} · {formatTime(entry.scannedAt)}
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}

      <div className="flex flex-col gap-[var(--sp-2)]">
        {flags.guardManualLookup && (
          <Button variant="ghost" asChild className="min-h-14">
            <Link to="/guard/manual">
              <ScanLine aria-hidden />Look up a pass manually
            </Link>
          </Button>
        )}
        <Button variant="ghost" asChild className="min-h-14">
          <Link to="/guard/shift-end">End this shift</Link>
        </Button>
      </div>
    </div>
  );
}
