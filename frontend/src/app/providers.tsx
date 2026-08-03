import { useEffect } from 'react';
import { QueryClientProvider } from '@tanstack/react-query';
import { TooltipProvider } from '@ui/index';
import { queryClient } from '@lib/query/queryClient';
import { setSessionExpiredHandler } from '@lib/api/interceptors';
import { ToastHost } from '@hooks/useToast';
import { ErrorBoundary } from '@components/feedback';
import { SessionBootstrap } from './SessionBootstrap';

export function Providers({ children }: { children: React.ReactNode }) {
  useEffect(() => {
    // The API layer must not import the router, so the redirect is injected.
    setSessionExpiredHandler(() => {
      queryClient.clear();
      const next = encodeURIComponent(window.location.pathname);
      window.location.assign(`/login?reason=expired&next=${next}`);
    });
  }, []);

  return (
    <ErrorBoundary region="root">
      <QueryClientProvider client={queryClient}>
        <TooltipProvider delayDuration={300}>
          <ToastHost>
            <SessionBootstrap>{children}</SessionBootstrap>
          </ToastHost>
        </TooltipProvider>
      </QueryClientProvider>
    </ErrorBoundary>
  );
}
