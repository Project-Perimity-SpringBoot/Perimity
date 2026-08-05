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

          /*
           * flex column with a capped height, so DialogBody's overflow-y-auto
           * has a bounded parent to scroll inside.
           *
           * Without this the dialog grew to fit its content and DialogFooter —
           * where the confirming action lives — was pushed off the bottom of
           * the viewport with no way to reach it. The body had overflow-y-auto
           * all along, but an auto-height parent means there is never any
           * overflow to scroll, so it did nothing.
           *
           * The symptom is easy to misread as "the button was never built",
           * because from the user's side those are indistinguishable.
           */
          'flex max-h-[85vh] flex-col',

          side === 'center'
            ? 'left-1/2 top-1/2 w-[calc(100vw-var(--sp-8))] max-w-lg -translate-x-1/2 -translate-y-1/2 rounded-[var(--r-lg)]'
            : 'inset-y-0 right-0 w-full max-w-xl border-l border-[var(--border)]',

          // Full-screen sheet on mobile, for both variants. max-h-none because
          // inset-0 already fixes the height and a cap would fight it.
          'max-sm:inset-0 max-sm:left-0 max-sm:top-0 max-sm:max-h-none max-sm:max-w-none',
          'max-sm:translate-x-0 max-sm:translate-y-0 max-sm:rounded-none',
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

/** shrink-0 so a long body cannot squeeze the title out of the way. */
export function DialogHeader({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn(
        'shrink-0 border-b border-[var(--border)] px-[var(--sp-6)] py-[var(--sp-4)] pr-[var(--sp-12)]',
        className,
      )}
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

/**
 * min-h-0 is load-bearing. A flex child defaults to min-height:auto, which
 * refuses to shrink below its content — so it would overflow the capped parent
 * and push the footer out regardless of overflow-y-auto. flex-1 lets it take
 * the space left between header and footer, and nothing more.
 */
export function DialogBody({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn('min-h-0 flex-1 overflow-y-auto px-[var(--sp-6)] py-[var(--sp-4)]', className)}
      {...props}
    />
  );
}

/**
 * shrink-0 pins the footer to the bottom of the dialog. This is the one that
 * was actually broken: the confirming action lives here, and a footer that can
 * be scrolled away is a dialog the user cannot complete.
 */
export function DialogFooter({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn(
        'flex shrink-0 items-center justify-end gap-[var(--sp-2)] border-t border-[var(--border)]',
        'bg-[var(--surface)] px-[var(--sp-6)] py-[var(--sp-3)]',
        className,
      )}
      {...props}
    />
  );
}
