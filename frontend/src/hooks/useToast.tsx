import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import { ToastItem, ToastProvider, ToastViewport, type ToastTone } from '@ui/index';
import { ApiError, ValidationError } from '@lib/api/errors';

interface ToastRecord {
  id: number;
  tone: ToastTone;
  title: string;
  description?: string;
}

interface ToastApi {
  success: (title: string, description?: string) => void;
  error: (title: string, description?: string) => void;
  info: (title: string, description?: string) => void;
  /**
   * Renders a typed API failure. Business-rule messages from the backend are
   * written for humans, so they are shown verbatim rather than replaced with
   * "Something went wrong".
   */
  fromError: (error: unknown, fallback?: string) => void;
}

const ToastContext = createContext<ToastApi | null>(null);

export function ToastHost({ children }: { children: React.ReactNode }) {
  const [items, setItems] = useState<ToastRecord[]>([]);

  const push = useCallback((tone: ToastTone, title: string, description?: string) => {
    setItems((prev) => [
      ...prev.slice(-3),
      { id: Date.now() + Math.random(), tone, title, ...(description ? { description } : {}) },
    ]);
  }, []);

  const api = useMemo<ToastApi>(
    () => ({
      success: (t, d) => push('success', t, d),
      error: (t, d) => push('error', t, d),
      info: (t, d) => push('info', t, d),
      fromError: (error, fallback = 'That request could not be completed.') => {
        if (error instanceof ValidationError) {
          push('error', 'Check the highlighted fields', error.formErrors[0]);
          return;
        }
        if (error instanceof ApiError) {
          push('error', error.message || fallback, error.formErrors[0]);
          return;
        }
        push('error', fallback);
      },
    }),
    [push],
  );

  return (
    <ToastContext.Provider value={api}>
      <ToastProvider swipeDirection="right" duration={6000}>
        {children}
        {items.map((item) => (
          <ToastItem
            key={item.id}
            tone={item.tone}
            title={item.title}
            {...(item.description ? { description: item.description } : {})}
            onOpenChange={(open) => {
              if (!open) setItems((prev) => prev.filter((i) => i.id !== item.id));
            }}
          />
        ))}
        <ToastViewport />
      </ToastProvider>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastApi {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used inside <ToastHost>');
  return ctx;
}
