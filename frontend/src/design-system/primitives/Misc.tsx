import * as SeparatorPrimitive from '@radix-ui/react-separator';
import * as ProgressPrimitive from '@radix-ui/react-progress';
import * as SwitchPrimitive from '@radix-ui/react-switch';
import * as AvatarPrimitive from '@radix-ui/react-avatar';
import * as TabsPrimitive from '@radix-ui/react-tabs';
import { cn } from '@lib/utils/cn';

export function Separator({ className, ...props }: SeparatorPrimitive.SeparatorProps) {
  return (
    <SeparatorPrimitive.Root
      className={cn('bg-[var(--border)] data-[orientation=horizontal]:h-px data-[orientation=vertical]:w-px', className)}
      {...props}
    />
  );
}

export interface ProgressProps {
  value: number;
  max?: number;
  /** e.g. "312 of 580" — rendered beside the bar and announced politely. */
  label?: string;
  indeterminate?: boolean;
  className?: string;
}

export function Progress({ value, max = 100, label, indeterminate, className }: ProgressProps) {
  const pct = max > 0 ? Math.min(100, Math.round((value / max) * 100)) : 0;
  return (
    <div className={cn('flex flex-col gap-[var(--sp-1)]', className)}>
      <ProgressPrimitive.Root
        value={indeterminate ? null : pct}
        className="h-2 w-full overflow-hidden rounded-[var(--r-pill)] bg-[var(--surface-sunken)]"
      >
        <ProgressPrimitive.Indicator
          className={cn(
            'h-full bg-[var(--brand-600)] transition-[width] duration-[var(--motion-base)] ease-[var(--ease-out)]',
            indeterminate && 'w-1/3 animate-pulse',
          )}
          style={indeterminate ? undefined : { width: `${pct}%` }}
        />
      </ProgressPrimitive.Root>
      {label && (
        <p className="text-caption text-[var(--ink-500)]" aria-live="polite">
          {label}
        </p>
      )}
    </div>
  );
}

export function Switch({ className, ...props }: SwitchPrimitive.SwitchProps) {
  return (
    <SwitchPrimitive.Root
      className={cn(
        'peer inline-flex h-5 w-9 shrink-0 items-center rounded-[var(--r-pill)] border-2 border-transparent',
        'transition-colors data-[state=checked]:bg-[var(--brand-600)] data-[state=unchecked]:bg-[var(--border-strong)]',
        className,
      )}
      {...props}
    >
      <SwitchPrimitive.Thumb className="pointer-events-none block size-4 rounded-[var(--r-circle)] bg-white shadow transition-transform data-[state=checked]:translate-x-4 data-[state=unchecked]:translate-x-0" />
    </SwitchPrimitive.Root>
  );
}

export function Avatar({ name, src, className }: { name: string; src?: string | null; className?: string }) {
  const initials = name
    .split(/\s+/)
    .slice(0, 2)
    .map((p) => p[0] ?? '')
    .join('')
    .toUpperCase();
  return (
    <AvatarPrimitive.Root
      className={cn(
        'inline-flex size-8 shrink-0 select-none items-center justify-center overflow-hidden rounded-[var(--r-circle)] bg-[var(--brand-50)]',
        className,
      )}
    >
      {src && <AvatarPrimitive.Image src={src} alt="" className="size-full object-cover" />}
      <AvatarPrimitive.Fallback className="text-caption text-[var(--brand-600)]">
        {initials || '?'}
      </AvatarPrimitive.Fallback>
    </AvatarPrimitive.Root>
  );
}

export const Tabs = TabsPrimitive.Root;

export function TabsList({ className, ...props }: TabsPrimitive.TabsListProps) {
  return (
    <TabsPrimitive.List
      className={cn('flex items-center gap-[var(--sp-1)] border-b border-[var(--border)]', className)}
      {...props}
    />
  );
}

export function TabsTrigger({ className, ...props }: TabsPrimitive.TabsTriggerProps) {
  return (
    <TabsPrimitive.Trigger
      className={cn(
        'relative px-[var(--sp-3)] py-[var(--sp-2)] text-small text-[var(--ink-500)] transition-colors',
        'data-[state=active]:text-[var(--ink-900)]',
        'after:absolute after:inset-x-0 after:-bottom-px after:h-[2px] after:bg-transparent',
        'data-[state=active]:after:bg-[var(--brand-600)]',
        className,
      )}
      {...props}
    />
  );
}

export const TabsContent = TabsPrimitive.Content;
