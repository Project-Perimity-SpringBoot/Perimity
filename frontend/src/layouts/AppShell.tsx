import { Suspense } from 'react';
import { Outlet } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { visitorRequestApi } from '@lib/api/services/gatepass.api';
import { requestKeys } from '@lib/query/keys';
import { POLL } from '@lib/query/queryClient';
import { useAuth } from '@hooks/useAuth';
import { useIsDesktop, useIsMobile } from '@hooks/useMediaQuery';
import { ErrorBoundary } from '@components/feedback';
import { SkeletonCards } from '@ui/index';
import { NAVIGATION, type NavItem } from './navigation';
import { Sidebar } from './Sidebar';
import { BottomTabBar } from './BottomTabBar';
import { TopBar } from './TopBar';
import { SessionBanner } from './SessionBanner';

/**
 * Desktop: sidebar + top bar. Tablet: collapsed icon rail. Mobile: bottom tabs.
 * One shell, three treatments — the brief's responsive rule, applied once here
 * rather than re-derived on every page.
 */
export function AppShell() {
  const { identity, can } = useAuth();
  const isMobile = useIsMobile();
  const isDesktop = useIsDesktop();

  const role = identity?.role ?? 'VISITOR';
  const items: NavItem[] = NAVIGATION[role].filter(
    (item) => !item.capability || can(item.capability),
  );

  const pending = useQuery({
    queryKey: requestKeys.pendingCount(),
    queryFn: () => visitorRequestApi.pendingCount(),
    enabled: can('request:viewQueue'),
    refetchInterval: POLL.pendingCountMs,
  });

  return (
    <div className="flex min-h-dvh bg-[var(--desk)]">
      {!isMobile && (
        <Sidebar
          items={items}
          badges={{ pendingRequests: pending.data ?? 0 }}
          variant={isDesktop ? 'full' : 'rail'}
        />
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <TopBar />
        <SessionBanner />

        <main
          className="flex-1 px-[var(--sp-4)] py-[var(--sp-6)] lg:px-[var(--sp-8)]"
          style={isMobile ? { paddingBottom: 'calc(var(--tabbar-h) + var(--sp-6))' } : undefined}
        >
          <div className="mx-auto w-full max-w-[1400px]">
            {/* One boundary per shell region: a crash in a table must not blank
                the navigation the user needs to escape it. */}
            <ErrorBoundary region="main">
              <Suspense fallback={<SkeletonCards />}>
                <Outlet />
              </Suspense>
            </ErrorBoundary>
          </div>
        </main>
      </div>

      {isMobile && <BottomTabBar items={items} />}
    </div>
  );
}
