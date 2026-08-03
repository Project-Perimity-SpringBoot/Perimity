import * as DialogPrimitive from '@radix-ui/react-dialog';
import { X } from 'lucide-react';
import { cn } from '@lib/utils/cn';

export const Dialog = DialogPrimitive.Root;
export const DialogTrigger = DialogPrimitive.Trigger;
export const DialogClose = DialogPrimitive.Close;

function Overlay({ className, ...props }: DialogPrimitive.DialogOverlayProps) {
  return (
    <DialogPrimitive.Overlay
      className={cn(
        'fixed inset-0 z-50 bg-black/40 backdrop-blur-[2px]',
        'data-[state=open]:animate-in data-[state=open]:fade-in',
        className,
      )}
      {...props}
    />
  );
}

export interface DialogContentProps extends DialogPrimitive.DialogContentProps {
  /** Right-hand panel on desktop, full-screen sheet below 640px. */
  side?: 'center' | 'right';
}

export function DialogContent({ className, children, side = 'center', ...props }: DialogContentProps) {
  return (
    <DialogPrimitive.Portal>
      <Overlay />
      <DialogPrimitive.Content
        className={cn(
          'fixed z-50 bg-[var(--surface)] shadow-[var(--sh-overlay)] outline-none',
          'transition-transform duration-[var(--motion-base)] ease-[var(--ease-out)]',
          side === 'center'
            ? 'left-1/2 top-1/2 w-[calc(100vw-var(--sp-8))] max-w-lg -translate-x-1/2 -translate-y-1/2 rounded-[var(--r-lg)]'
            : 'inset-y-0 right-0 w-full max-w-xl border-l border-[var(--border)]',
          // Full-screen sheet on mobile, for both variants.
          'max-sm:inset-0 max-sm:left-0 max-sm:top-0 max-sm:max-w-none max-sm:translate-x-0 max-sm:translate-y-0 max-sm:rounded-none',
          className,
        )}
        {...props}
      >
        {children}
        <DialogPrimitive.Close
          aria-label="Close"
          className="absolute right-[var(--sp-4)] top-[var(--sp-4)] rounded-[var(--r-sm)] p-1 text-[var(--ink-500)] hover:bg-[var(--surface-sunken)]"
        >
          <X className="size-4" aria-hidden />
        </DialogPrimitive.Close>
      </DialogPrimitive.Content>
    </DialogPrimitive.Portal>
  );
}

export function DialogHeader({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn('border-b border-[var(--border)] px-[var(--sp-6)] py-[var(--sp-4)] pr-[var(--sp-12)]', className)}
      {...props}
    />
  );
}

export function DialogTitle({ className, ...props }: DialogPrimitive.DialogTitleProps) {
  return <DialogPrimitive.Title className={cn('text-h3', className)} {...props} />;
}

export function DialogDescription({ className, ...props }: DialogPrimitive.DialogDescriptionProps) {
  return (
    <DialogPrimitive.Description
      className={cn('text-small text-[var(--ink-500)]', className)}
      {...props}
    />
  );
}

export function DialogBody({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('px-[var(--sp-6)] py-[var(--sp-4)] overflow-y-auto', className)} {...props} />;
}

export function DialogFooter({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn(
        'flex items-center justify-end gap-[var(--sp-2)] border-t border-[var(--border)] px-[var(--sp-6)] py-[var(--sp-3)]',
        className,
      )}
      {...props}
    />
  );
}
