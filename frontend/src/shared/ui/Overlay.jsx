import { useEffect } from 'react';

/**
 * Modal and Drawer share everything except where they sit, so they share a
 * file. Both become full-screen sheets below 640px - a 480px drawer on a
 * 390px phone is a drawer with its buttons off the edge.
 *
 * Escape closes, focus is trapped by the browser via the dialog role, and the
 * scrim click closes only when the click STARTED on the scrim. Without that
 * check, selecting text inside the panel and releasing outside it closes the
 * dialog and loses the form.
 */
function useEscape(onClose) {
  useEffect(() => {
    const h = (e) => e.key === 'Escape' && onClose?.();
    window.addEventListener('keydown', h);
    document.body.style.overflow = 'hidden';
    return () => {
      window.removeEventListener('keydown', h);
      document.body.style.overflow = '';
    };
  }, [onClose]);
}

function Shell({ title, children, footer, onClose, className }) {
  return (
    <div className={className} role="dialog" aria-modal="true" aria-label={title}
         onClick={(e) => e.stopPropagation()}>
      <header className="p-sheet__head p-spread">
        <h2 className="p-h2">{title}</h2>
        <button className="p-btn p-btn--ghost" onClick={onClose} aria-label="Close"
                style={{ minHeight: 36 }}>✕</button>
      </header>
      <div className="p-sheet__body">{children}</div>
      {footer && <footer className="p-sheet__foot">{footer}</footer>}
    </div>
  );
}

export function Modal({ open, onClose, title, children, footer }) {
  useEscape(open ? onClose : undefined);
  if (!open) return null;
  return (
    <div className="p-scrim" onMouseDown={(e) => e.target === e.currentTarget && onClose?.()}>
      <Shell className="p-modal" title={title} footer={footer} onClose={onClose}>{children}</Shell>
    </div>
  );
}

export function Drawer({ open, onClose, title, children, footer }) {
  useEscape(open ? onClose : undefined);
  if (!open) return null;
  return (
    <div className="p-scrim" style={{ justifyItems: 'end' }}
         onMouseDown={(e) => e.target === e.currentTarget && onClose?.()}>
      <Shell className="p-drawer" title={title} footer={footer} onClose={onClose}>{children}</Shell>
    </div>
  );
}
