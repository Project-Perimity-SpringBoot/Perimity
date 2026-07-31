import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * Camera QR decode with NO npm dependency.
 *
 * Uses the browser's built-in BarcodeDetector. Chrome on Android has it, which
 * is what a guard is holding; Safari and Firefox do not, which is why the
 * manual-entry path beside this is a first-class screen and not a fallback
 * nobody tested.
 *
 * Adding html5-qrcode later is a drop-in — keep this hook's shape and swap the
 * body. Everything above it reads { supported, scanning, start, stop, videoRef }.
 *
 * TWO THINGS THAT MATTER AT A GATE
 *  - `facingMode: 'environment'` — the rear camera. The front one points at
 *    the guard.
 *  - The cooldown. Without it one held-up pass fires the same scan twenty
 *    times a second, and the log fills with duplicates that look like a
 *    person entering twenty times.
 */
const COOLDOWN_MS = 2500;

export function useScanner(onCode) {
  const videoRef = useRef(null);
  const streamRef = useRef(null);
  const rafRef = useRef(null);
  const lastRef = useRef({ code: null, at: 0 });

  const [supported] = useState(() => typeof window !== 'undefined' && 'BarcodeDetector' in window);
  const [scanning, setScanning] = useState(false);
  const [error, setError] = useState(null);

  const stop = useCallback(() => {
    cancelAnimationFrame(rafRef.current);
    streamRef.current?.getTracks().forEach((t) => t.stop());
    streamRef.current = null;
    setScanning(false);
  }, []);

  const start = useCallback(async () => {
    setError(null);
    if (!supported) { setError(new Error('This browser cannot decode QR codes. Use manual lookup.')); return; }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'environment' },
      });
      streamRef.current = stream;
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
        await videoRef.current.play();
      }
      setScanning(true);

      // eslint-disable-next-line no-undef
      const detector = new BarcodeDetector({ formats: ['qr_code'] });

      const tick = async () => {
        if (!streamRef.current || !videoRef.current) return;
        try {
          const found = await detector.detect(videoRef.current);
          const code = found?.[0]?.rawValue;
          const now = Date.now();
          if (code && !(code === lastRef.current.code && now - lastRef.current.at < COOLDOWN_MS)) {
            lastRef.current = { code, at: now };
            onCode(code);
          }
        } catch {
          // A single failed frame is normal — motion blur, bad light. Keep going.
        }
        rafRef.current = requestAnimationFrame(tick);
      };
      rafRef.current = requestAnimationFrame(tick);
    } catch (e) {
      // Almost always a denied camera permission. Say so plainly.
      setError(new Error(
        e?.name === 'NotAllowedError'
          ? 'Camera access was refused. Allow it in the browser, or use manual lookup.'
          : 'Could not start the camera. Use manual lookup.'));
    }
  }, [supported, onCode]);

  useEffect(() => stop, [stop]);

  return { videoRef, supported, scanning, error, start, stop };
}
