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

  const setAt = (index: number, char: string) => {
    const next = value.padEnd(OTP_RULES.length, ' ').split('');
    next[index] = char;
    const joined = next.join('').replace(/\s/g, '');
    onChange(joined);
    if (joined.length === OTP_RULES.length) onComplete?.(joined);
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
          maxLength={1}
          disabled={disabled}
          aria-label={`Digit ${i + 1}`}
          aria-invalid={invalid || undefined}
          value={value[i] ?? ''}
          onChange={(e) => {
            const digit = e.target.value.replace(/\D/g, '').slice(-1);
            if (!digit) return;
            setAt(i, digit);
            refs.current[i + 1]?.focus();
          }}
          onKeyDown={(e) => {
            if (e.key === 'Backspace' && !value[i]) refs.current[i - 1]?.focus();
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
