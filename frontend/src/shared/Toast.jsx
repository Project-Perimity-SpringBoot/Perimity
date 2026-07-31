import { createContext, useCallback, useContext, useState } from 'react';

/**
 * One toast queue for the whole app.
 *
 * Day 18 asks for "a toast on every API failure, never a blank screen". Having
 * it in the shell from Day 13 means nobody writes their own, and error styling
 * does not end up different on six screens.
 */
const ToastContext = createContext(null);

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);

  const push = useCallback((message, type = 'error') => {
    const id = crypto.randomUUID();
    setToasts((t) => [...t, { id, message, type }]);
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 5000);
  }, []);

  const api = {
    error: (m) => push(m, 'error'),
    success: (m) => push(m, 'success'),
    info: (m) => push(m, 'info'),
  };

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="toast-stack">
        {toasts.map((t) => (
          <div key={t.id} className={`toast toast-${t.type}`}>
            {t.message}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used inside <ToastProvider>');
  return ctx;
}
