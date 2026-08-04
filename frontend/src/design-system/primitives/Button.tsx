import { forwardRef } from 'react';
import { Slot } from '@radix-ui/react-slot';
import { cva, type VariantProps } from 'class-variance-authority';
import { Loader2 } from 'lucide-react';
import { cn } from '@lib/utils/cn';

const button = cva(
  'inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-[var(--r-sm)] ' +
    'font-medium transition-colors duration-[var(--motion-fast)] ' +
    'disabled:pointer-events-none disabled:opacity-50 [&_svg]:shrink-0',
  {
    variants: {
      variant: {
        primary: 'bg-[var(--brand-600)] text-white hover:bg-[color-mix(in_srgb,var(--brand-600)_88%,black)]',
        secondary: 'bg-[var(--surface)] text-[var(--ink-900)] border border-[var(--border-strong)] hover:bg-[var(--surface-subtle)]',
        danger: 'bg-[var(--deny-solid)] text-white hover:bg-[color-mix(in_srgb,var(--deny-solid)_88%,black)]',
        ghost: 'text-[var(--ink-700)] hover:bg-[var(--surface-sunken)]',
        link: 'text-[var(--brand-600)] underline-offset-4 hover:underline p-0 h-auto',
      },
      size: {
        sm: 'h-8 px-3 text-[var(--t-small-size)] [&_svg]:size-4',
        md: 'h-9 px-4 text-[var(--t-body-size)] [&_svg]:size-4',
        lg: 'h-11 px-6 text-[var(--t-body-size)] [&_svg]:size-5',
        icon: 'h-9 w-9 [&_svg]:size-4',
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
