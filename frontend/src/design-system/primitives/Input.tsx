import { forwardRef } from 'react';
import { cn } from '@lib/utils/cn';

export interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  invalid?: boolean;
  mono?: boolean;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ className, invalid, mono, ...props }, ref) => (
    <input
      ref={ref}
      aria-invalid={invalid || undefined}
      className={cn(
        'h-[var(--control-h)] w-full rounded-[var(--r-sm)] border bg-[var(--surface)] px-[var(--sp-3)]',
        'text-[var(--t-body-size)] text-[var(--ink-900)]',
        'placeholder:text-[var(--ink-400)]',
        'transition-colors duration-[var(--motion-fast)]',
        'hover:border-[var(--ink-400)]',
        'disabled:cursor-not-allowed disabled:bg-[var(--surface-sunken)] disabled:opacity-60',
        'disabled:hover:border-[var(--border-strong)]',
        invalid ? 'border-[var(--deny-solid)]' : 'border-[var(--border-strong)]',
        mono && 'font-[var(--font-mono)] tracking-[var(--t-mono-tracking)]',
        className,
      )}
      {...props}
    />
  ),
);
Input.displayName = 'Input';

export const Textarea = forwardRef<
  HTMLTextAreaElement,
  React.TextareaHTMLAttributes<HTMLTextAreaElement> & { invalid?: boolean }
>(({ className, invalid, ...props }, ref) => (
  <textarea
    ref={ref}
    aria-invalid={invalid || undefined}
    className={cn(
      'min-h-24 w-full rounded-[var(--r-sm)] border bg-[var(--surface)] px-[var(--sp-3)] py-[var(--sp-2)]',
      'text-[var(--t-body-size)] text-[var(--ink-900)] placeholder:text-[var(--ink-400)]',
      'transition-colors duration-[var(--motion-fast)] hover:border-[var(--ink-400)]',
      'disabled:cursor-not-allowed disabled:bg-[var(--surface-sunken)] disabled:opacity-60',
      invalid ? 'border-[var(--deny-solid)]' : 'border-[var(--border-strong)]',
      className,
    )}
    {...props}
  />
));
Textarea.displayName = 'Textarea';

/**
 * A native <select>, styled to match Input.
 *
 * The Radix Select in this design system is the right control for filters and
 * anywhere the trigger needs rich content. This one exists for react-hook-form
 * fields, where `register()` needs a real form control it can wire a ref and a
 * change event to — driving Radix from RHF means a Controller per field, and
 * ten Controllers to render ten dropdowns is not a trade worth making.
 *
 * It is also the better control on a phone: the OS picker beats a custom
 * listbox for a one-handed user every time.
 */
export const NativeSelect = forwardRef<
  HTMLSelectElement,
  React.SelectHTMLAttributes<HTMLSelectElement> & { invalid?: boolean }
>(({ className, invalid, children, ...props }, ref) => (
  <select
    ref={ref}
    aria-invalid={invalid || undefined}
    className={cn(
      'h-[var(--control-h)] w-full appearance-none rounded-[var(--r-sm)] border bg-[var(--surface)] px-[var(--sp-3)]',
      'text-[var(--t-body-size)] text-[var(--ink-900)]',
      'transition-colors duration-[var(--motion-fast)]',
      'hover:border-[var(--ink-400)]',
      'disabled:cursor-not-allowed disabled:bg-[var(--surface-sunken)] disabled:opacity-60',
      'disabled:hover:border-[var(--border-strong)]',
      // The chevron is a background image so the control keeps native keyboard
      // and OS-picker behaviour that a custom listbox would have to reimplement.
      'bg-[length:16px] bg-[right_0.6rem_center] bg-no-repeat pr-9',
      "bg-[url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%236b7280' stroke-width='1.75' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='m6 9 6 6 6-6'/%3E%3C/svg%3E\")]",
      invalid ? 'border-[var(--deny-solid)]' : 'border-[var(--border-strong)]',
      className,
    )}
    {...props}
  >
    {children}
  </select>
));
NativeSelect.displayName = 'NativeSelect';
