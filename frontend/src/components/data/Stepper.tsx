import { Check } from 'lucide-react';
import { cn } from '@lib/utils/cn';

export interface StepperProps {
  steps: string[];
  /** Zero-based index of the step being worked on. */
  current: number;
  className?: string;
}

/**
 * The progress rail above a multi-step flow.
 *
 * Perimity has three of these — the student import (configure the form, upload
 * the sheet, confirm), the bulk pass onboarding (choose, validate, confirm),
 * and profile completion — and none of them showed the user where they were.
 * The import page came closest, with a hand-written "Step 1 of 3" chip inside
 * one panel, which told you the step you were on and nothing about the ones
 * either side of it.
 *
 * A real <ol> with aria-current, because the visual rail is the whole content
 * of this component: a screen reader given three unlabelled circles learns
 * nothing, and the same reader given a list with "step 2, current" learns
 * everything the sighted user gets.
 */
export function Stepper({ steps, current, className }: StepperProps) {
  return (
    <ol className={cn('flex items-start', className)}>
      {steps.map((label, i) => {
        const done = i < current;
        const active = i === current;
        const last = i === steps.length - 1;

        return (
          <li
            key={label}
            className={cn('flex min-w-0 items-start', !last && 'flex-1')}
            aria-current={active ? 'step' : undefined}
          >
            <div className="flex min-w-0 flex-col items-center gap-[var(--sp-2)]">
              <span
                className={cn(
                  'flex size-7 shrink-0 items-center justify-center rounded-[var(--r-circle)] border-2',
                  'transition-colors duration-[var(--motion-base)]',
                  done && 'border-[var(--brand-600)] bg-[var(--brand-600)] text-white',
                  active && 'border-[var(--brand-600)] bg-[var(--surface)]',
                  !done && !active && 'border-[var(--border-strong)] bg-[var(--surface)]',
                )}
              >
                {done ? (
                  <Check className="size-4" aria-hidden />
                ) : (
                  <span
                    className={cn(
                      'size-2 rounded-[var(--r-circle)]',
                      active ? 'bg-[var(--brand-600)]' : 'bg-[var(--border-strong)]',
                    )}
                    aria-hidden
                  />
                )}
              </span>
              <span
                className={cn(
                  'text-small max-w-28 truncate text-center',
                  active || done ? 'text-[var(--ink-900)]' : 'text-[var(--ink-500)]',
                )}
              >
                {label}
              </span>
            </div>

            {/* The connector is centred on the circle, not the label, so it
                stays a straight line when the labels are different lengths. */}
            {!last && (
              <span
                aria-hidden
                className={cn(
                  'mt-[13px] h-0.5 min-w-4 flex-1 rounded-[var(--r-pill)]',
                  'transition-colors duration-[var(--motion-base)]',
                  done ? 'bg-[var(--brand-600)]' : 'bg-[var(--border-strong)]',
                )}
              />
            )}
          </li>
        );
      })}
    </ol>
  );
}
