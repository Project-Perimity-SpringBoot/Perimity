import { AlertTriangle, Lock, SearchX, ServerCrash, WifiOff } from 'lucide-react';
import { Button } from '@ui/index';
import {
  ApiError, ForbiddenError, NetworkError, NotFoundError, RateLimitError,
} from '@lib/api/errors';
import { EmptyState } from './EmptyState';

/**
 * Turns a typed API failure into a screen that says what happened and what to
 * do next. The backend writes its business messages for humans, so they are
 * rendered verbatim rather than replaced with a generic string.
 */
export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  if (error instanceof ForbiddenError) {
    return (
      <EmptyState
        icon={Lock}
        heading="You do not have access to this"
        description={error.message}
      />
    );
  }

  if (error instanceof NotFoundError) {
    return <EmptyState icon={SearchX} heading="Not found" description={error.message} />;
  }

  if (error instanceof RateLimitError) {
    return (
      <EmptyState
        icon={AlertTriangle}
        heading="Too many attempts"
        description={`${error.message} Try again in ${error.retryAfterSeconds ?? 60} seconds.`}
      />
    );
  }

  if (error instanceof NetworkError) {
    return (
      <EmptyState
        icon={WifiOff}
        heading="Could not reach the server"
        description="Check your connection and try again."
        {...(onRetry ? { action: <Button onClick={onRetry}>Try again</Button> } : {})}
      />
    );
  }

  const message = error instanceof ApiError ? error.message : 'An unexpected error occurred.';
  return (
    <EmptyState
      icon={ServerCrash}
      heading="Something did not load"
      description={message}
      {...(onRetry ? { action: <Button onClick={onRetry}>Try again</Button> } : {})}
    />
  );
}

/** Inline banner for a form-level failure, above the fieldset. */
export function FormError({ messages }: { messages: string[] }) {
  if (messages.length === 0) return null;
  return (
    <div
      role="alert"
      className="rounded-[var(--r-sm)] border border-[var(--deny-solid)] bg-[var(--deny-bg)] px-[var(--sp-3)] py-[var(--sp-2)]"
    >
      <ul className="text-small space-y-[var(--sp-1)] text-[var(--deny-fg)]">
        {messages.map((m) => (
          <li key={m}>{m}</li>
        ))}
      </ul>
    </div>
  );
}
