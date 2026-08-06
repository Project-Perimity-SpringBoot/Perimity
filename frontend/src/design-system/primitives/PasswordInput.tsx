import { forwardRef, useState } from 'react';
import { Eye, EyeOff } from 'lucide-react';
import { cn } from '@lib/utils/cn';
import { Input, type InputProps } from './Input';

/**
 * A password field with a show/hide toggle.
 *
 * ==========================================================================
 * WHY THIS IS A PRIMITIVE AND NOT COPIED INTO EACH PAGE
 * ==========================================================================
 * The same ten lines - relative wrapper, a `type` ternary, a state flag, a
 * button with a swapped icon and a swapped aria-label - existed four times
 * across LoginPage and ChangePasswordPage, and three more were needed for
 * ResetPasswordPage and GuardLoginPage. Seven copies of a control means seven
 * chances for one of them to lose `type="button"` and start submitting the
 * form, or to keep a stale aria-label that tells a screen reader "Show
 * password" while the password is already showing.
 *
 * ==========================================================================
 * THE DETAILS THAT MATTER
 * ==========================================================================
 * `type="button"` is not decoration. A <button> inside a <form> defaults to
 * type="submit", so without it, revealing your password submits the form -
 * half-typed, and on a login screen that spends a failed attempt against the
 * lockout counter.
 *
 * The accessible name changes with the state rather than staying "Toggle
 * password". A screen reader user needs to know which way the switch is about
 * to move, and "Toggle" answers neither what it does now nor what it will do.
 *
 * The button stays in the tab order. Removing it with tabIndex={-1} would tidy
 * up Tab-through, at the cost of making the control unreachable for anyone not
 * using a mouse - which is the group most likely to need to check what they
 * typed.
 *
 * `size-8` rather than the icon's own bounds, because the tap target has to be
 * usable on GuardLoginPage's oversized inputs - a guard at a gate, one-handed,
 * often in the dark. `top-1/2 -translate-y-1/2` keeps it centred whatever
 * height the caller gives the input.
 *
 * Visibility deliberately does NOT reset when the form re-renders or the value
 * changes. Somebody who asked to see their password is usually mid-correction,
 * and hiding it again under them is the opposite of the help they asked for.
 *
 * ==========================================================================
 * WHY ::-ms-reveal IS HIDDEN
 * ==========================================================================
 * Edge injects its own reveal control into every <input type="password">,
 * drawn inside the field at the right - so the user sees TWO eyes, ours and
 * the browser's, sitting next to each other and disagreeing about state.
 *
 * It appears only while the field is focused and has content, so it is easy to
 * miss: the other password fields on the same screen look correct at the same
 * moment. Chrome and Firefox add nothing, so it does not reproduce there
 * either.
 *
 * Ours wins rather than Edge's because Edge's control cannot be styled, is
 * absent in every other browser, and does not exist at all once the input
 * flips to type="text" - which would mean the reveal button vanishing the
 * instant you used it.
 */
export type PasswordInputProps = Omit<InputProps, 'type'>;

export const PasswordInput = forwardRef<HTMLInputElement, PasswordInputProps>(
  ({ className, ...props }, ref) => {
    const [visible, setVisible] = useState(false);

    return (
      <div className="relative">
        <Input
          ref={ref}
          type={visible ? 'text' : 'password'}
          className={cn(
            // pr-10 keeps the text clear of the button.
            'pr-10',
            // Edge's built-in reveal control. See the note above.
            '[&::-ms-reveal]:hidden [&::-ms-clear]:hidden',
            // Caller classes last, so a page can still override.
            className,
          )}
          {...props}
        />
        <button
          type="button"
          onClick={() => setVisible((v) => !v)}
          aria-label={visible ? 'Hide password' : 'Show password'}
          className={cn(
            'absolute right-2 top-1/2 flex size-8 -translate-y-1/2',
            'items-center justify-center rounded-[var(--r-sm)]',
            'text-[var(--ink-500)] transition-colors hover:text-[var(--ink-900)]',
          )}
        >
          {visible
            ? <EyeOff className="size-4" aria-hidden />
            : <Eye className="size-4" aria-hidden />}
        </button>
      </div>
    );
  },
);
PasswordInput.displayName = 'PasswordInput';
