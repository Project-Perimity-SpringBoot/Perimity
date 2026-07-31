import Button from './Button';

/** Icon, one heading, one line, one action. More than one action means the
 *  screen has not decided what it wants the user to do next. */
export default function EmptyState({ icon = '○', title, message, actionLabel, onAction }) {
  return (
    <div className="p-empty">
      <span className="p-empty__icon" aria-hidden>{icon}</span>
      <h3 className="p-h3">{title}</h3>
      {message && <p className="p-body p-muted" style={{ margin: 0, maxWidth: 420 }}>{message}</p>}
      {actionLabel && <Button onClick={onAction}>{actionLabel}</Button>}
    </div>
  );
}
