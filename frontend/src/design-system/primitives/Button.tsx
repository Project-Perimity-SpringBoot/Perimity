import { forwardRef } from 'react';
import { Slot } from '@radix-ui/react-slot';
import { cva, type VariantProps } from 'class-variance-authority';
import { Loader2 } from 'lucide-react';
import { cn } from '@lib/utils/cn';

/**
 * `active:translate-y-px` is the only transform in the system, and it is one
 * pixel: a button should acknowledge the press, and anything larger turns a
 * mis-click into a moving target. Colour still does most of the work.
 *
 * `outline` is a real variant now rather than a `secondary` with a className
 * bolted on at the call site. Four screens had built their own version of it,
 * which is how the product ended up with three different button borders.
 */
const button = cva(
  'inline-flex items-center justify-center gap-[var(--sp-2)] whitespace-nowrap rounded-[var(--r-sm)] ' +
    'font-medium transition-[background-color,border-color,color,box-shadow,transform] ' +
    'duration-[var(--motion-fast)] active:translate-y-px ' +
    'disabled:pointer-events-none disabled:opacity-50 [&_svg]:shrink-0',
  {
    variants: {
      variant: {
        primary:
          'bg-[var(--brand-600)] text-white shadow-[0_1px_2px_rgba(19,19,22,0.08)] ' +
          'hover:bg-[color-mix(in_srgb,var(--brand-600)_88%,black)]',
        secondary:
          'bg-[var(--surface)] text-[var(--ink-900)] border border-[var(--border-strong)] ' +
          'shadow-[0_1px_2px_rgba(19,19,22,0.04)] hover:bg-[var(--surface-subtle)] hover:border-[var(--ink-400)]',
        outline:
          'bg-transparent text-[var(--brand-600)] border border-[var(--brand-200)] ' +
          'hover:bg-[var(--brand-50)] hover:border-[var(--brand-300)]',
        danger:
          'bg-[var(--deny-solid)] text-white shadow-[0_1px_2px_rgba(19,19,22,0.08)] ' +
          'hover:bg-[color-mix(in_srgb,var(--deny-solid)_88%,black)]',
        ghost: 'text-[var(--ink-700)] hover:bg-[var(--surface-sunken)] hover:text-[var(--ink-900)]',
        link: 'text-[var(--brand-600)] underline-offset-4 hover:underline p-0 h-auto active:translate-y-0',
      },
      size: {
        sm: 'h-[var(--control-h-sm)] px-[var(--sp-3)] text-[var(--t-small-size)] [&_svg]:size-4',
        md: 'h-[var(--control-h)] px-[var(--sp-4)] text-[var(--t-body-size)] [&_svg]:size-4',
        lg: 'h-[var(--control-h-lg)] px-[var(--sp-6)] text-[var(--t-body-size)] [&_svg]:size-5',
        icon: 'h-[var(--control-h)] w-[var(--control-h)] [&_svg]:size-4',
      },
      block: { true: 'w-full' },
    },
    defaultVariants: { variant: 'primary', size: 'md' },
  },
);

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof button> {
  asChild?: boolean;
  loading?: boolean;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, block, asChild, loading, children, disabled, ...props }, ref) => {
    const Comp = asChild ? Slot : 'button';
    return (
      <Comp
        ref={ref}
        className={cn(button({ variant, size, block }), className)}
        disabled={disabled ?? loading}
        aria-busy={loading || undefined}
        {...props}
      >
        {loading ? (
          <>
            <Loader2 className="animate-spin" aria-hidden />
            {children}
          </>
        ) : (
          children
        )}
      </Comp>
    );
  },
);
Button.displayName = 'Button';
