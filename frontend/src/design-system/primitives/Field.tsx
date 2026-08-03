import { useId } from 'react';
import * as LabelPrimitive from '@radix-ui/react-label';
import { cn } from '@lib/utils/cn';

export interface FieldProps {
  label: string;
  htmlFor?: string;
  required?: boolean;
  /** Sits under the control until an error replaces it. */
  hint?: string;
  error?: string;
  /** Marks a field that moves the holder's pass to PAUSED when changed. */
  pausesPass?: boolean;
  className?: string;
  children: (ids: { id: string; describedBy: string | undefined }) => React.ReactNode;
}

/**
 * Label + control + hint/error, wired with aria-describedby so a screen reader
 * announces the error with the field rather than as loose text.
 */
export function Field({
  label, htmlFor, required, hint, error, pausesPass, className, children,
}: FieldProps) {
  const generated = useId();
  const id = htmlFor ?? generated;
  const messageId = `${id}-message`;
  const message = error ?? hint;

  return (
    <div className={cn('flex flex-col gap-[var(--sp-1)]', className)}>
      <LabelPrimitive.Root
        htmlFor={id}
        className="text-small text-[var(--ink-700)] flex items-center gap-[var(--sp-1)]"
      >
        {label}
        {required && (
          <span className="text-[var(--deny-solid)]" aria-hidden>
            *
          </span>
        )}
        {pausesPass && (
          <span
            className="text-caption text-[var(--ink-500)]"
            title="Changing this pauses the pass until a faculty member re-approves it"
          >
            · pauses pass
          </span>
        )}
      </LabelPrimitive.Root>

      {children({ id, describedBy: message ? messageId : undefined })}

      {message && (
        <p
          id={messageId}
          className={cn(
            'text-caption',
            error ? 'text-[var(--deny-fg)]' : 'text-[var(--ink-500)]',
          )}
        >
          {message}
        </p>
      )}
    </div>
  );
}
