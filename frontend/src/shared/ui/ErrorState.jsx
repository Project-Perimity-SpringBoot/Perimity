import Button from './Button';

/**
 * Page-level failure. Distinct from a toast, which is for something that failed
 * while the page still works, and from a field error, which is the user's turn.
 *
 * Always offers a retry. A dead end with no action is how a user decides the
 * product is broken rather than the request.
 */
export default function ErrorState({
  title = 'Something went wrong', message, onRetry, retryLabel = 'Try again',
}) {
  return (
    <div className="p-error-page">
      <span className="p-empty__icon" aria-hidden>!</span>
      <h3 className="p-h3">{title}</h3>
      {message && <p className="p-body p-muted" style={{ margin: 0, maxWidth: 460 }}>{message}</p>}
      {onRetry && <Button variant="secondary" onClick={onRetry}>{retryLabel}</Button>}
    </div>
  );
}
