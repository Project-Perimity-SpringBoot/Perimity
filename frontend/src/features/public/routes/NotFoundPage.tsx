import { Link } from 'react-router';
import { SearchX } from 'lucide-react';
import { Button } from '@ui/index';
import { EmptyState } from '@components/feedback';

export default function NotFoundPage() {
  return (
    <div className="flex min-h-dvh items-center justify-center bg-[var(--desk)] px-[var(--sp-4)]">
      <EmptyState
        icon={SearchX}
        heading="That page does not exist"
        description="The link may be out of date, or the record may have been removed."
        action={
          <Button asChild variant="secondary">
            <Link to="/">Back to the start</Link>
          </Button>
        }
      />
    </div>
  );
}
