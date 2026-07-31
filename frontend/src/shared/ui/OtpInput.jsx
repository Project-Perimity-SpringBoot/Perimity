import { useRef } from 'react';

/**
 * Six mono boxes. Auto-advance, backspace-retreat, and paste-a-whole-code -
 * because the code arrives in an email and people paste it.
 *
 * The countdown and attempts line are props rather than internal state: the
 * server owns both numbers (10 minute expiry, 5 attempts, 60s resend), and a
 * timer the client invents will disagree with it.
 */
export default function OtpInput({
  value = '', onChange, length = 6, error, resendIn, attemptsLeft, onResend,
}) {
  const refs = useRef([]);
  const chars = value.padEnd(length).split('').slice(0, length);

  const setAt = (i, ch) => {
    const next = chars.map((c, idx) => (idx === i ? ch : c)).join('').trimEnd();
    onChange?.(next);
  };

  const handleKey = (i) => (e) => {
    if (e.key === 'Backspace' && !chars[i].trim() && i > 0) refs.current[i - 1]?.focus();
  };

  const handleInput = (i) => (e) => {
    const digits = e.target.value.replace(/\D/g, '');
    if (!digits) { setAt(i, ' '); return; }
    // Pasting the whole code into any box fills the row.
    if (digits.length > 1) {
      onChange?.(digits.slice(0, length));
      refs.current[Math.min(digits.length, length - 1)]?.focus();
      return;
    }
    setAt(i, digits);
    if (i < length - 1) refs.current[i + 1]?.focus();
  };

  return (
    <div className="p-stack" style={{ gap: 'var(--s-2)' }}>
      <div className={`p-otp ${error ? 'p-otp--error' : ''}`}>
        {chars.map((c, i) => (
          <input
            key={i}
            ref={(el) => (refs.current[i] = el)}
            className="p-otp__box"
            inputMode="numeric"
            autoComplete={i === 0 ? 'one-time-code' : 'off'}
            maxLength={length}
            value={c.trim()}
            onChange={handleInput(i)}
            onKeyDown={handleKey(i)}
            aria-label={`Digit ${i + 1}`}
          />
        ))}
      </div>

      {error && <span className="p-field__error">{error}</span>}

      <div className="p-spread">
        <span className="p-caption">
          {attemptsLeft != null && `${attemptsLeft} attempt${attemptsLeft === 1 ? '' : 's'} remaining`}
        </span>
        <button
          type="button" className="p-btn p-btn--ghost"
          style={{ minHeight: 'auto', padding: '4px 8px' }}
          disabled={resendIn > 0} onClick={onResend}
        >
          {resendIn > 0
            ? `Resend in 0:${String(resendIn).padStart(2, '0')}`
            : 'Send a new code'}
        </button>
      </div>
    </div>
  );
}
