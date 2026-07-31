/**
 * Every clickable thing in the product. Four intents, five states.
 *
 * `loading` disables as well as spins - a button that shows a spinner but
 * still accepts clicks is how you get two visitor requests from one submit.
 */
export default function Button({
  variant = 'primary', size, block, loading, disabled, icon, children, ...rest
}) {
  return (
    <button
      className={[
        'p-btn', `p-btn--${variant}`,
        size === 'lg' && 'p-btn--lg',
        block && 'p-btn--block',
      ].filter(Boolean).join(' ')}
      disabled={disabled || loading}
      {...rest}
    >
      {loading ? <span className="p-spin" aria-hidden /> : icon}
      {children}
    </button>
  );
}
