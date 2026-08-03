import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * Camera decode for the gate scanner.
 *
 * ==========================================================================
 * WHY BarcodeDetector AND NOT A LIBRARY
 * ==========================================================================
 * A guard scans on a phone, on campus wifi, standing in front of a queue.
 * jsQR is ~45 KB of JavaScript decoding frames on the main thread; the native
 * BarcodeDetector is zero bytes and runs off it. Chrome on Android has it,
 * which is the actual deployment target.
 *
 * Safari and Firefox do NOT. That is exactly why the typed-code path on the
 * scanner screen is a first-class control rather than a hidden fallback — on
 * those browsers it is the only way to scan, and a guard must not discover
 * that at the gate. `supported` is returned so the screen can say so plainly.
 *
 * If a polyfill is ever wanted it belongs behind a dynamic import inside this
 * hook, so the 45 KB only loads on the browsers that need it. The hook's shape
 * is designed for that swap.
 *
 * ==========================================================================
 * THE COOLDOWN IS NOT A NICETY
 * ==========================================================================
 * A QR code held in front of a camera decodes every frame — 30 times a second.
 * Without the cooldown one person standing still would POST thirty scans,
 * write thirty entry logs, and trip the repeat-entry AMBER rule against
 * themselves. The gate would appear to reject someone for the crime of not
 * moving.
 */

const COOLDOWN_MS = 2500;
const TICK_MS = 250;

interface BarcodeDetectorLike {
  detect(source: CanvasImageSource): Promise<Array<{ rawValue: string }>>;
}
type BarcodeDetectorCtor = new (options?: { formats?: string[] }) => BarcodeDetectorLike;

function detectorCtor(): BarcodeDetectorCtor | null {
  const w = window as unknown as { BarcodeDetector?: BarcodeDetectorCtor };
  return w.BarcodeDetector ?? null;
}

export type CameraState = 'idle' | 'starting' | 'running' | 'denied' | 'unsupported' | 'failed';

export function useQrScanner(onToken: (token: string) => void) {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const timerRef = useRef<number | null>(null);
  const lastRef = useRef<{ token: string; at: number }>({ token: '', at: 0 });

  // Held in a ref so the polling loop always calls the latest handler without
  // being torn down and restarted on every render.
  const onTokenRef = useRef(onToken);
  useEffect(() => { onTokenRef.current = onToken; }, [onToken]);

  const [state, setState] = useState<CameraState>('idle');

  const stop = useCallback(() => {
    if (timerRef.current !== null) {
      window.clearInterval(timerRef.current);
      timerRef.current = null;
    }
    streamRef.current?.getTracks().forEach((t) => t.stop());
    streamRef.current = null;
    setState('idle');
  }, []);

  const start = useCallback(async () => {
    const Ctor = detectorCtor();
    if (!Ctor) { setState('unsupported'); return; }

    setState('starting');
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        // The back camera, not the selfie one. Without this a phone opens
        // facing the guard and they have to work out why nothing scans.
        video: { facingMode: { ideal: 'environment' } },
        audio: false,
      });
      streamRef.current = stream;

      const element = videoRef.current;
      if (!element) { stop(); return; }
      element.srcObject = stream;
      await element.play();

      const detector = new Ctor({ formats: ['qr_code'] });

      timerRef.current = window.setInterval(() => {
        void (async () => {
          const el = videoRef.current;
          if (!streamRef.current || !el) return;
          try {
            const found = await detector.detect(el);
            const token = found[0]?.rawValue;
            if (!token) return;

            const now = Date.now();
            const isRepeatOfSameCode =
              token === lastRef.current.token && now - lastRef.current.at < COOLDOWN_MS;
            if (isRepeatOfSameCode) return;

            lastRef.current = { token, at: now };
            onTokenRef.current(token);
          } catch {
            // A single failed frame is not an error worth surfacing - the next
            // one is 250ms away. Only getUserMedia failing is worth a state.
          }
        })();
      }, TICK_MS);

      setState('running');
    } catch (err) {
      // NotAllowedError is the user declining the permission prompt, which is a
      // recoverable choice and gets its own copy on screen. Anything else is a
      // camera that will not open at all.
      const denied = err instanceof DOMException && err.name === 'NotAllowedError';
      setState(denied ? 'denied' : 'failed');
      stop();
      setState(denied ? 'denied' : 'failed');
    }
  }, [stop]);

  // Release the camera when the guard leaves the screen. A phone with the torch
  // LED on and no scanner in front of it is a support call.
  useEffect(() => stop, [stop]);

  return { videoRef, state, start, stop, supported: detectorCtor() !== null };
}
