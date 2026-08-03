import { useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Loader2 } from 'lucide-react';
import { authApi } from '@lib/api/services/auth.api';
import { authKeys } from '@lib/query/keys';
import { tokenStore } from '@lib/auth/tokenStore';
import { useAuthStore } from '@stores/authStore';

/**
 * A rehydrated token proves only that it was signed and has not expired. It
 * cannot know the account was deactivated an hour ago, so the shell confirms
 * with GET /api/auth/me before rendering anything behind a guard.
 */
export function SessionBootstrap({ children }: { children: React.ReactNode }) {
  const setProfile = useAuthStore((s) => s.setProfile);
  const signOut = useAuthStore((s) => s.signOut);
  const hasToken = tokenStore.isAuthenticated();

  const me = useQuery({
    queryKey: authKeys.me(),
    queryFn: () => authApi.me(),
    enabled: hasToken,
    retry: false,
    staleTime: 5 * 60_000,
  });

  useEffect(() => {
    if (me.data) setProfile(me.data);
  }, [me.data, setProfile]);

  useEffect(() => {
    // A 401 is already handled by the interceptor. Anything else that stops us
    // confirming identity is still a reason not to trust the session.
    if (me.isError) signOut();
  }, [me.isError, signOut]);

  if (hasToken && me.isPending) {
    return (
      <div className="flex min-h-dvh items-center justify-center bg-[var(--desk)]">
        <Loader2 className="size-6 animate-spin text-[var(--brand-600)]" aria-label="Loading" />
      </div>
    );
  }

  return <>{children}</>;
}
