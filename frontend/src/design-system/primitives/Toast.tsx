import * as ToastPrimitive from '@radix-ui/react-toast';
import { CheckCircle2, Info, XCircle } from 'lucide-react';
import { cn } from '@lib/utils/cn';

export const ToastProvider = ToastPrimitive.Provider;
export const ToastViewport = () => (
  <ToastPrimitive.Viewport className="fixed bottom-0 right-0 z-[100] flex w-full max-w-sm flex-col gap-[var(--sp-2)] p-[var(--sp-4)] outline-none max-sm:top-0 max-sm:bottom-auto" />
);

export type ToastTone = 'success' | 'error' | 'info';

const ICON: Record<ToastTone, typeof Info> = {
  success: CheckCircle2,
  error: XCircle,
  info: Info,
};

export interface ToastItemProps extends ToastPrimitive.ToastProps {
  tone: ToastTone;
  title: string;
  description?: string;
}

export function ToastItem({ tone, title, description, className, ...props }: ToastItemProps) {
  const Icon = ICON[tone];
  return (
    <ToastPrimitive.Root
      className={cn(
        'surface-card flex items-start gap-[var(--sp-3)] p-[var(--sp-4)]',
        'data-[state=open]:animate-in data-[state=open]:slide-in-from-right',
        className,
      )}
      {...props}
    >
      <Icon
        aria-hidden
        className={cn(
          'mt-[2px] size-4 shrink-0',
          tone === 'success' && 'text-[var(--allow-fg)]',
          tone === 'error' && 'text-[var(--deny-fg)]',
          tone === 'info' && 'text-[var(--brand-600)]',
        )}
      />
      <div className="min-w-0 flex-1">
        <ToastPrimitive.Title className="text-body-md text-[var(--ink-900)]">
          {title}
        </ToastPrimitive.Title>
        {description && (
          <ToastPrimitive.Description className="text-small mt-[2px] text-[var(--ink-500)] break-words">
            {description}
          </ToastPrimitive.Description>
        )}
      </div>
    </ToastPrimitive.Root>
  );
}
