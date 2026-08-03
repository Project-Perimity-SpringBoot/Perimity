import { PauseCircle } from 'lucide-react';
import { Link } from 'react-router';
import type { GatePassResponse } from '@/types/gatepass.types';

/**
 * Shown above everything on every student screen when any pass is PAUSED.
 *
 * ==========================================================================
 * WHY IT SITS ABOVE THE GREETING
 * ==========================================================================
 * A paused pass does not scan. If a student walks to the gate not knowing, the
 * failure happens in front of a queue with a guard who cannot fix it — and the
 * student's own screen had the answer the whole time.
 *
 * So it renders before the greeting, not after: "Good morning, Anjali" above a
 * notice that she cannot get in today is the wrong order of importance.
 *
 * It renders NOTHING when nothing is paused. No empty container, no reserved
 * space, no "all good" variant. A banner that is always present is a banner
 * nobody reads, which costs exactly the attention this one needs on the day it
 * matters.
 *
 * ==========================================================================
 * WHY THIS IS NOT AMBER
 * ==========================================================================
 * The obvious styling for "warning" is --review-*, and tokens.css forbids it:
 * the verdict colours belong to the guard's scan result and nothing else,
 * because using them elsewhere trains the eye to stop reading them as a verdict.
 *
 * So this is a neutral surface and the WORDS carry the weight — which is the
 * same rule StatusBadge follows. It is still the most prominent thing on the
 * page by position and by role="alert", neither of which needs colour.
 */
export function PausedBanner({ passes }: { passes: GatePassResponse[] }) {
  const paused = passes.filter((pass) => pass.status === 'PAUSED');
  if (paused.length === 0) return null;

  const one = paused.length === 1 ? paused[0] : undefined;

  return (
    <section
      // assertive, not polite: a screen-reader user should hear this before
      // whatever they navigated here to do.
      role="alert"
      className="flex items-start gap-[var(--sp-3)] rounded-[var(--r-md)] border
                 border-[var(--status-border)] bg-[var(--status-bg)] p-[var(--sp-4)]"
    >
      <PauseCircle aria-hidden className="mt-[2px] size-5 shrink-0 text-[var(--ink-700)]" />
      <div className="min-w-0">
        <h2 className="text-body-md text-[var(--ink-900)]">
          {paused.length === 1
            ? 'Your pass is paused'
            : `${paused.length} of your passes are paused`}
        </h2>
        <p className="text-small mt-[var(--sp-1)] text-[var(--ink-700)]">
          A paused pass will not scan at the gate. This happens automatically after a
          change to your name, photo, government ID or department, and clears once staff
          re-verify your profile. You do not need to do anything.
        </p>
        {one && (
          <Link
            to={`/student/passes/${one.id}`}
            className="text-small mt-[var(--sp-2)] inline-block text-[var(--brand-600)] underline-offset-4 hover:underline"
          >
            See which pass
          </Link>
        )}
      </div>
    </section>
  );
}
