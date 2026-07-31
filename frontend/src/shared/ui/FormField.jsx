/**
 * Label, control, helper, error - in one place so all ~200 inputs across the
 * product line up and describe their errors identically.
 *
 * `pausesPass` marks the four fields the SRS treats as sensitive: name, photo,
 * government ID and department. Editing one moves the holder's pass to PAUSED
 * until a faculty member re-approves it. The glyph is on the field itself, not
 * only in a banner, because a banner is read once and a field is read at the
 * moment of the decision.
 */
export default function FormField({
  label, type = 'text', required, error, help, pausesPass,
  as, options, id, ...rest
}) {
  const fieldId = id || `f-${label?.toLowerCase().replace(/\W+/g, '-')}`;
  const Control = as === 'select' ? 'select' : as === 'textarea' ? 'textarea' : 'input';
  const cls = as === 'select' ? 'p-select' : as === 'textarea' ? 'p-textarea' : 'p-input';

  return (
    <div className={`p-field ${error ? 'p-field--error' : ''}`}>
      {label && (
        <label className="p-field__label" htmlFor={fieldId}>
          {label}
          {required && <span className="p-field__req" aria-hidden>*</span>}
          {pausesPass && (
            <span className="p-field__warn p-caption" title="Changing this pauses the pass">
              {' '}⚠ pauses pass
            </span>
          )}
        </label>
      )}

      <Control
        id={fieldId}
        className={cls}
        type={as ? undefined : type}
        aria-invalid={!!error}
        aria-describedby={error ? `${fieldId}-err` : help ? `${fieldId}-help` : undefined}
        {...rest}
      >
        {as === 'select'
          ? options?.map((o) => (
              <option key={o.value ?? o} value={o.value ?? o}>{o.label ?? o}</option>
            ))
          : undefined}
      </Control>

      {error
        ? <span id={`${fieldId}-err`} className="p-field__error">{error}</span>
        : help && <span id={`${fieldId}-help`} className="p-field__help">{help}</span>}
    </div>
  );
}
