import { useCallback, useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router';
import { Camera, CameraOff, Keyboard, Monitor, ScanLine, WifiOff } from 'lucide-react';
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
 * THERE IS NO GUARD DASHBOARD, AND THAT IS THE DESIGN
 * ==========================================================================
 * A guard opens this app for exactly one reason. Shift counters, recent
 * activity and gate details are all things that would compete with the
 * viewfinder for the one screen that matters, so they live on the end-shift
 * summary instead. The gate bar at the top (rendered by GuardShell) is the only
 * permanent chrome.
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
 * verify a pass, and since today it fails fast rather than hanging — the
 * circuit breaker refuses immediately instead of waiting out a timeout per
 * scan. Either way the guard is told the scanner is down, not that the person
 * is refused.
 *
 * ==========================================================================
 * FIRE AND SHOW
 * ==========================================================================
 * Decode, POST, render the verdict. There is no confirmation tap in between,
 * because the queue is the thing this screen fights and the verdict is its own
 * confirmation.
 */
export default function ScannerPage() {
  const toast = useToast();
  const queryClient = useQueryClient();

  const [verdict, setVerdict] = useState<ScanResponse | null>(null);
  const [typed, setTyped] = useState('');
  const [showTyped, setShowTyped] = useState(false);
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

  const cameraUnavailable =
    isDesktop || !camera.supported || camera.state === 'denied' || camera.state === 'unsupported'
    || camera.state === 'failed';

  return (
    <div className="flex flex-col gap-[var(--sp-5)] p-[var(--sp-4)]">
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

      {/* ---- Screen 8: desktop fallback ---- */}
      {isDesktop ? (
        <section className="surface-card flex flex-col items-center gap-[var(--sp-4)] p-[var(--sp-8)] text-center">
          <Monitor aria-hidden className="size-10 text-[var(--ink-500)]" />
          <h1 className="text-h2 text-[var(--ink-900)]">Scanning is for the gate phone</h1>
          <p className="text-body max-w-[42ch] text-[var(--ink-700)]">
            This screen is open on a desktop, so there is no camera to point at a pass. You
            can still type a code below — useful for checking a pass a guard has queried.
          </p>
        </section>
      ) : (
        <section className="flex flex-col gap-[var(--sp-4)]">
          <div className="relative overflow-hidden rounded-[var(--r-lg)] bg-black">
            {/* A live viewfinder, not media. No caption track exists or could:
                there is no audio and no recorded content, and the element is
                aria-hidden because what matters to a screen-reader user is the
                verdict, which is announced with role="alert". */}
            <video
              ref={camera.videoRef}
              aria-hidden
              className="aspect-[3/4] w-full object-cover"
              playsInline
              muted
            />
            {camera.state === 'running' && (
              <span
                aria-hidden
                className="pointer-events-none absolute inset-[12%] rounded-[var(--r-lg)]
                           border-2 border-white/70"
              />
            )}
          </div>

          {camera.state === 'idle' && (
            <Button size="lg" block onClick={() => void camera.start()}>
              <Camera aria-hidden />Start scanning
            </Button>
          )}
          {camera.state === 'starting' && <Skeleton className="h-12 w-full" />}
          {camera.state === 'running' && (
            <Button size="lg" variant="secondary" block onClick={camera.stop}>
              Pause camera
            </Button>
          )}

          {/* ---- camera permission denied ---- */}
          {camera.state === 'denied' && (
            <div className="surface-card flex flex-col gap-[var(--sp-3)] p-[var(--sp-4)]">
              <p className="flex items-center gap-[var(--sp-2)] text-body-md text-[var(--ink-900)]">
                <CameraOff aria-hidden className="size-5" />
                Camera access was refused
              </p>
              <p className="text-small text-[var(--ink-700)]">
                Allow the camera in your browser settings and reload. Until then, type the
                code from the pass below — it works exactly the same way.
              </p>
            </div>
          )}

          {(camera.state === 'unsupported' || camera.state === 'failed') && (
            <div className="surface-card flex flex-col gap-[var(--sp-3)] p-[var(--sp-4)]">
              <p className="text-body-md text-[var(--ink-900)]">
                {camera.state === 'unsupported'
                  ? 'This browser cannot decode QR codes'
                  : 'The camera could not be opened'}
              </p>
              <p className="text-small text-[var(--ink-700)]">
                {camera.state === 'unsupported'
                  ? 'Chrome on Android supports it; Safari and Firefox do not. Use the typed code below — it is not a workaround, it is the same scan.'
                  : 'Another app may be using it. Type the code below instead.'}
              </p>
            </div>
          )}
        </section>
      )}

      {/* ---- typed code: first-class, not a fallback ---- */}
      <section className="surface-card flex flex-col gap-[var(--sp-3)] p-[var(--sp-4)]">
        {!showTyped && !cameraUnavailable ? (
          <Button variant="ghost" onClick={() => setShowTyped(true)}>
            <Keyboard aria-hidden />Type a code instead
          </Button>
        ) : (
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
                  aria-describedby={describedBy}
                  value={typed}
                  onChange={(e) => setTyped(e.target.value)}
                />
              )}
            </Field>
            <Button type="submit" size="lg" block loading={scan.isPending} disabled={!typed.trim()}>
              <ScanLine aria-hidden />Check this pass
            </Button>
          </form>
        )}
      </section>

      {flags.guardManualLookup && (
        <Button variant="ghost" asChild>
          <Link to="/guard/manual">Look up a pass manually</Link>
        </Button>
      )}

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

      <Button variant="ghost" asChild>
        <Link to="/guard/shift-end">End this shift</Link>
      </Button>
    </div>
  );
}
