import { Link } from 'react-router';
import { Construction } from 'lucide-react';
import { Button } from '@ui/index';
import { EmptyState } from './EmptyState';
import { PageHeader } from '@components/data';

/**
 * The landing page for a role whose phase has not been built yet.
 *
 * Phase 0 ships the shell for all six roles, but only auth has screens. Without
 * this, signing in as a Student lands on a route that does not exist and the
 * catch-all bounces them to /404 — which looks like the shell is broken rather
 * than like the phase is pending.
 *
 * Each owner deletes their own instance from router.tsx when their phase lands.
 */
export function PhasePending({
  phase, owner, area, screens,
}: {
  phase: number;
  owner: string;
  area: string;
  screens: number;
}) {
  return (
    <div className="flex flex-col gap-[var(--sp-6)]">
      <PageHeader title={area} description={`Phase ${phase} · ${owner}`} />
      <EmptyState
        icon={Construction}
        heading={`Phase ${phase} has not been built yet`}
        description={`${owner} owns these ${screens} screens. The shell, the design system and the API layer underneath are ready — this folder is waiting for its phase.`}
        action={
          <Button asChild variant="secondary">
            <Link to="/">Back to start</Link>
          </Button>
        }
      />
    </div>
  );
}
