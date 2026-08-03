import * as SelectPrimitive from '@radix-ui/react-select';
import { Check, ChevronDown } from 'lucide-react';
import { cn } from '@lib/utils/cn';

export const Select = SelectPrimitive.Root;
export const SelectValue = SelectPrimitive.Value;

export function SelectTrigger({ className, children, ...props }: SelectPrimitive.SelectTriggerProps) {
  return (
    <SelectPrimitive.Trigger
      className={cn(
        'flex h-9 w-full items-center justify-between gap-[var(--sp-2)] rounded-[var(--r-sm)]',
        'border border-[var(--border-strong)] bg-[var(--surface)] px-3',
        'text-[var(--t-body-size)] text-[var(--ink-900)]',
        'data-[placeholder]:text-[var(--ink-400)] disabled:opacity-60',
        className,
      )}
      {...props}
    >
      {children}
      <SelectPrimitive.Icon asChild>
        <ChevronDown className="size-4 text-[var(--ink-500)]" aria-hidden />
      </SelectPrimitive.Icon>
    </SelectPrimitive.Trigger>
  );
}

export function SelectContent({ className, children, ...props }: SelectPrimitive.SelectContentProps) {
  return (
    <SelectPrimitive.Portal>
      <SelectPrimitive.Content
        position="popper"
        sideOffset={4}
        className={cn(
          'z-50 max-h-72 min-w-[var(--radix-select-trigger-width)] overflow-hidden',
          'rounded-[var(--r-md)] border border-[var(--border)] bg-[var(--surface)] shadow-[var(--sh-overlay)]',
          className,
        )}
        {...props}
      >
        <SelectPrimitive.Viewport className="p-[var(--sp-1)]">{children}</SelectPrimitive.Viewport>
      </SelectPrimitive.Content>
    </SelectPrimitive.Portal>
  );
}

export function SelectItem({ className, children, ...props }: SelectPrimitive.SelectItemProps) {
  return (
    <SelectPrimitive.Item
      className={cn(
        'relative flex cursor-pointer select-none items-center gap-[var(--sp-2)] rounded-[var(--r-sm)]',
        'px-[var(--sp-2)] py-[var(--sp-2)] text-[var(--t-body-size)] outline-none',
        'data-[highlighted]:bg-[var(--surface-sunken)] data-[state=checked]:bg-[var(--brand-50)]',
        className,
      )}
      {...props}
    >
      <SelectPrimitive.ItemIndicator asChild>
        <Check className="size-4 text-[var(--brand-600)]" aria-hidden />
      </SelectPrimitive.ItemIndicator>
      <SelectPrimitive.ItemText>{children}</SelectPrimitive.ItemText>
    </SelectPrimitive.Item>
  );
}
