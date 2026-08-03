import * as TooltipPrimitive from '@radix-ui/react-tooltip';
import { cn } from '@lib/utils/cn';

export const TooltipProvider = TooltipPrimitive.Provider;

export function Tooltip({
  content, children, side = 'right',
}: {
  content: React.ReactNode;
  children: React.ReactNode;
  side?: 'top' | 'right' | 'bottom' | 'left';
}) {
  return (
    <TooltipPrimitive.Root delayDuration={300}>
      <TooltipPrimitive.Trigger asChild>{children}</TooltipPrimitive.Trigger>
      <TooltipPrimitive.Portal>
        <TooltipPrimitive.Content
          side={side}
          sideOffset={6}
          className={cn(
            'z-50 rounded-[var(--r-sm)] bg-[var(--ink-900)] px-[var(--sp-2)] py-[var(--sp-1)]',
            'text-caption text-white shadow-[var(--sh-overlay)]',
          )}
        >
          {content}
        </TooltipPrimitive.Content>
      </TooltipPrimitive.Portal>
    </TooltipPrimitive.Root>
  );
}
