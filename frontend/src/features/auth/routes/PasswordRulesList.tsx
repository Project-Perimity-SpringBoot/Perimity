import { Check, Circle } from 'lucide-react';
import { PASSWORD_RULES } from '../schemas/auth.schemas';
import { cn } from '@lib/utils/cn';

/**
 * The five rules the backend actually enforces. The design mockup asks for
 * "12 characters and a symbol"; the regex says 8 with no symbol, so this
 * follows the regex — a checklist that lies is worse than none.
 */
export function PasswordRulesList({ value }: { value: string }) {
  return (
    <ul className="flex flex-col gap-[var(--sp-1)]" aria-live="polite">
      {PASSWORD_RULES.map((rule) => {
        const passed = rule.test(value);
        return (
          <li key={rule.label} className="text-caption flex items-center gap-[var(--sp-2)]">
            {passed ? (
              <Check className="size-3 text-[var(--allow-fg)]" aria-hidden />
            ) : (
              <Circle className="size-3 text-[var(--ink-400)]" aria-hidden />
            )}
            <span className={cn(passed ? 'text-[var(--ink-700)]' : 'text-[var(--ink-500)]')}>
              {rule.label}
            </span>
            <span className="sr-only">{passed ? 'met' : 'not met'}</span>
          </li>
        );
      })}
    </ul>
  );
}
