import { useEffect, useRef } from 'react';
import { OTP_RULES } from '@lib/validation/patterns';
import { cn } from '@lib/utils/cn';

export interface OtpInputProps {
  value: string;
  onChange: (value: string) => void;
  onComplete?: (value: string) => void;
  invalid?: boolean;
  disabled?: boolean;
  autoFocus?: boolean;
}

/** Six mono boxes. Paste of a whole code fills every box at once. */
export function OtpInput({ value, onChange, onComplete, invalid, disabled, autoFocus }: OtpInputProps) {
  const refs = useRef<(HTMLInputElement | null)[]>([]);

  useEffect(() => {
    if (autoFocus) refs.current[0]?.focus();
  }, [autoFocus]);

  /**
   * Writes one box. An empty char CLEARS that box.
   *
   * Spaces are the placeholder for "not filled yet", and only trailing ones are
   * stripped - collapsing all of them would slide later digits leftwards when
   * you clear one in the middle, so correcting the third digit would silently
   * rewrite the fourth and fifth.
   */
  const setAt = (index: number, char: string) => {
    const next = value.padEnd(OTP_RULES.length, ' ').split('');
    next[index] = char || ' ';
    const joined = next.join('').trimEnd();
    onChange(joined);
    if (joined.replace(/\s/g, '').length === OTP_RULES.length) onComplete?.(joined);
  };

  return (
    <div className="flex gap-[var(--sp-2)]" role="group" aria-label="One-time code">
      {Array.from({ length: OTP_RULES.length }, (_, i) => (
        <input
          key={i}
          ref={(el) => {
            refs.current[i] = el;
          }}
          inputMode="numeric"
          autoComplete={i === 0 ? 'one-time-code' : 'off'}
          /*
           * NOT maxLength={1}.
           *
           * With it, a box that already holds a digit silently rejects the next
           * keystroke - so typing at any speed dropped characters: the handler
           * moves focus only after React re-renders, and anything arriving
           * before that hit a full box and was discarded. Typing 598738 landed
           * a single 5.
           *
           * It also broke SMS autofill, which delivers all six characters to
           * one box at once: the old handler took slice(-1) and threw five
           * away.
           */
          maxLength={OTP_RULES.length}
          disabled={disabled}
          aria-label={`Digit ${i + 1}`}
          aria-invalid={invalid || undefined}
          value={value[i] ?? ''}
          onChange={(e) => {
            const typed = e.target.value.replace(/\D/g, '');

            // Cleared. Honour it - returning early here is what once made a
            // wrong digit unfixable without reloading.
            if (!typed) {
              setAt(i, '');
              return;
            }

            /*
             * Spread everything that arrived across this box and the ones
             * after it, rather than keeping one character. Covers fast typing,
             * a paste into the middle, and autofill, with no special cases.
             */
            const next = value.padEnd(OTP_RULES.length, ' ').split('');
            typed.split('').forEach((char, offset) => {
              if (i + offset < OTP_RULES.length) next[i + offset] = char;
            });

            const joined = next.join('').trimEnd();
            onChange(joined);
            if (joined.replace(/\s/g, '').length === OTP_RULES.length) onComplete?.(joined);

            // Land on the box after the last one filled, or the final box.
            const landing = Math.min(i + typed.length, OTP_RULES.length - 1);
            refs.current[landing]?.focus();
          }}
          onKeyDown={(e) => {
            if (e.key === 'Backspace') {
              // Filled box: clear it and stay. Empty box: step back and clear
              // that one, which is what a person expects from backspace.
              if (value[i] && value[i] !== ' ') {
                e.preventDefault();
                setAt(i, '');
              } else {
                e.preventDefault();
                setAt(i - 1, '');
                refs.current[i - 1]?.focus();
              }
              return;
            }
            if (e.key === 'Delete') {
              e.preventDefault();
              setAt(i, '');
              return;
            }
            if (e.key === 'ArrowLeft') refs.current[i - 1]?.focus();
            if (e.key === 'ArrowRight') refs.current[i + 1]?.focus();
          }}
          onPaste={(e) => {
            e.preventDefault();
            const pasted = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, OTP_RULES.length);
            if (!pasted) return;
            onChange(pasted);
            if (pasted.length === OTP_RULES.length) onComplete?.(pasted);
            refs.current[Math.min(pasted.length, OTP_RULES.length - 1)]?.focus();
          }}
          className={cn(
            'text-mono size-12 rounded-[var(--r-sm)] border bg-[var(--surface)] text-center text-[18px]',
            'text-[var(--ink-900)] transition-colors disabled:opacity-60',
            invalid ? 'border-[var(--deny-solid)]' : 'border-[var(--border-strong)]',
          )}
        />
      ))}
    </div>
  );
}
