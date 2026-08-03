import { Link } from 'react-router';
import { Lock } from 'lucide-react';
import { Button } from '@ui/index';
import { EmptyState } from '@components/feedback';
import { useAuth } from '@hooks/useAuth';
import { LANDING_ROUTE } from '@lib/auth/permissions';

export default function ForbiddenPage() {
  const { role } = useAuth();
  return (
    <div className="flex min-h-dvh items-center justify-center bg-[var(--desk)] px-[var(--sp-4)]">
      <EmptyState
        icon={Lock}
        heading="You do not have access to this"
        description="Your role does not permit this action. If you think that is wrong, ask your campus administrator."
        action={
          <Button asChild variant="secondary">
            <Link to={role ? LANDING_ROUTE[role] : '/'}>Back to your dashboard</Link>
          </Button>
        }
      />
    </div>
  );
}
