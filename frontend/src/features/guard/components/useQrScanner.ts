import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * Camera decode for the gate scanner.
 *
 * ==========================================================================
 * TWO DECODERS, AND THE FAST ONE IS STILL FIRST
 * ==========================================================================
 * `BarcodeDetector` is zero bytes and runs off the main thread. It is used
 * whenever it exists, which on the real deployment target — Chrome on an
 * Android phone — it does.
 *
 * It does NOT exist on Windows, and never will at any Chrome version: the API
 * is backed by OS-level detection that Android, ChromeOS and macOS provide and
 * Windows does not. MDN says plainly that sites are expected to polyfill it.
 * That is what the jsQR branch below is.
 *
 * jsQR is loaded by DYNAMIC IMPORT, so the ~45 KB never reaches the phone at
 * the gate. It downloads only on the machines that cannot decode without it,
 * which are by definition the desks and laptops, not the queue.
 *
 * ==========================================================================
 * WHY THE FALLBACK DOWNSCALES
 * ==========================================================================
 * jsQR decodes on the main thread. A 1080p frame is two million pixels, four
 * times a second, on the same thread that has to stay responsive. Capping the
 * long edge at 640px cuts that by about nine tenths and costs nothing in
 * practice — a QR held up to a webcam is far larger in frame than the ~100px
 * a decode actually needs.
 *
 * ==========================================================================
 * THE COOLDOWN IS NOT A NICETY
 * ==========================================================================
 * A QR code held in front of a camera decodes every frame. Without the
 * cooldown one person standing still would POST thirty scans, write thirty
 * entry logs, and trip the repeat-entry AMBER rule against themselves. The
 * gate would appear to reject someone for the crime of not moving.
 */

const COOLDOWN_MS = 2500;
const TICK_MS = 250;
const MAX_EDGE = 640;

interface BarcodeDetectorLike {
  detect(source: CanvasImageSource): Promise<Array<{ rawValue: string }>>;
}
type BarcodeDetectorCtor = new (options?: { formats?: string[] }) => BarcodeDetectorLike;

function detectorCtor(): BarcodeDetectorCtor | null {
  const w = window as unknown as { BarcodeDetector?: BarcodeDetectorCtor };
  return w.BarcodeDetector ?? null;
}

/** One frame in, a token or null out. Both decoders wear this shape. */
type Decode = (video: HTMLVideoElement) => Promise<string | null>;

async function makeDecoder(): Promise<Decode | null> {
  const Ctor = detectorCtor();

  if (Ctor) {
    const detector = new Ctor({ formats: ['qr_code'] });
    return async (video) => {
      const found = await detector.detect(video);
      return found[0]?.rawValue ?? null;
    };
  }

  let jsQR: typeof import('jsqr').default;
  try {
    ({ default: jsQR } = await import('jsqr'));
  } catch {
    // The bundle is missing or the chunk failed to load. Nothing here can
    // recover from that, and the screen already has a typed-code path.
    return null;
  }

  const canvas = document.createElement('canvas');
  const ctx = canvas.getContext('2d', { willReadFrequently: true });
  if (!ctx) return null;

  return async (video) => {
    const vw = video.videoWidth;
    const vh = video.videoHeight;
    if (!vw || !vh) return null;

    const scale = Math.min(1, MAX_EDGE / Math.max(vw, vh));
    const w = Math.round(vw * scale);
    const h = Math.round(vh * scale);
    canvas.width = w;
    canvas.height = h;
    ctx.drawImage(video, 0, 0, w, h);

    const { data } = ctx.getImageData(0, 0, w, h);
    // dontInvert: a printed or on-screen pass is dark-on-light. Trying the
    // inverted pass as well doubles the work for a case that does not occur.
    return jsQR(data, w, h, { inversionAttempts: 'dontInvert' })?.data ?? null;
  };
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
    setState('starting');

    // Before the camera, not after. Asking for a permission we then cannot use
    // is the one order that wastes the guard's tap.
    const decode = await makeDecoder();
    if (!decode) { setState('unsupported'); return; }

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

      timerRef.current = window.setInterval(() => {
        void (async () => {
          const el = videoRef.current;
          if (!streamRef.current || !el) return;
          try {
            const token = await decode(el);
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
      stop();
      setState(denied ? 'denied' : 'failed');
    }
  }, [stop]);

  // Release the camera when the guard leaves the screen. A phone with the torch
  // LED on and no scanner in front of it is a support call.
  useEffect(() => stop, [stop]);

  /*
   * `supported` is now true everywhere: a browser without BarcodeDetector gets
   * jsQR instead. It is kept in the returned shape because the screen still
   * needs a way to be told when decoding is impossible — that now arrives as
   * state 'unsupported' after start(), which is the only moment we can know
   * whether the chunk loads.
   */
  return { videoRef, state, start, stop, supported: true };
}
