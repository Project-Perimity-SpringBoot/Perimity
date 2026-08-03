import { Link } from 'react-router';
import { ArrowLeft, Construction } from 'lucide-react';
import { Button } from '@ui/index';
import { EmptyState } from '@components/feedback';
import { PageHeader } from '@components/data';

/**
 * Phase 6 screens 6 and 7 — manual lookup, and the manual entry it leads to.
 *
 * ==========================================================================
 * SHIPPED DARK. THE ENDPOINTS DO NOT EXIST.
 * ==========================================================================
 * The build plan lists `GET /api/guard/passes/{code}` and
 * `POST /api/guard/entries`. Neither is in guard-service — this is blocker B8,
 * and `VITE_ENABLE_GUARD_MANUAL_LOOKUP` exists precisely for it. The route is
 * only reachable when that flag is on, and this is what it says until then.
 *
 * ==========================================================================
 * WHY THE BACKEND WAS NOT JUST ADDED ALONGSIDE THIS
 * ==========================================================================
 * A manual entry writes into the register WITHOUT a scan. That is a different
 * kind of write from everything else guard-service does, and it needs its own
 * decisions before any UI is built on top of it:
 *
 *   - who may record one, given a guard already has the GUARD role
 *   - whether a reason is mandatory, and whether free text is enough
 *   - how it is distinguished in the register afterwards, so an auditor can
 *     tell a scanned entry from a typed one
 *   - what stops it becoming the path of least resistance on a busy morning
 *
 * The entry log's whole value is that it is evidence rather than a claim. An
 * endpoint that lets a guard type someone in is the one place that property can
 * be lost, so it is worth its own change with its own tests rather than riding
 * along with eight screens.
 *
 * When it lands, screens 6 and 7 belong here: a lookup form, then a result with
 * an explicit "Record entry" button. Two steps, deliberately — unlike a scan,
 * which is fire-and-show. The extra tap is the point: a typed entry should feel
 * like a decision, because it is one.
 */
export default function ManualEntryPage() {
  return (
    <div className="flex flex-col gap-[var(--sp-5)] p-[var(--sp-4)]">
      <Button variant="link" asChild className="self-start">
        <Link to="/guard"><ArrowLeft aria-hidden />Back to scanning</Link>
      </Button>

      <PageHeader title="Look up a pass" />

      <EmptyState
        icon={Construction}
        heading="Manual lookup is not available yet"
        description="Recording an entry without a scan needs its own audit trail before it can be switched on. Until then, use the typed-code path on the scanner — it verifies the pass properly and leaves the same evidence a camera scan would."
        action={
          <Button asChild>
            <Link to="/guard">Go to the scanner</Link>
          </Button>
        }
      />
    </div>
  );
}
