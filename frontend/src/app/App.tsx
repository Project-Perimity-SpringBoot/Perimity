import { QueryClientProvider, useQuery } from '@tanstack/react-query';
import { queryClient } from '@lib/query/queryClient';
import { authApi } from '@lib/api/services/auth.api';
import { tokenStore } from '@lib/auth/tokenStore';
import { capabilitiesFor } from '@lib/auth/permissions';
import { config, flags } from '@lib/config';

/**
 * Phase 2 verification harness, not a product screen. It exercises the whole
 * foundation end to end — axios instance, interceptors, normalizer, token
 * store, capability resolution — and is deleted in Phase 4 when the real
 * router and shell arrive.
 */
function FoundationCheck() {
  const ping = useQuery({
    queryKey: ['ping', 'auth'],
    queryFn: () => authApi.ping(),
    retry: false,
  });

  const identity = tokenStore.identity();
  const capabilities = [...capabilitiesFor(identity)];

  return (
    <main style={{ fontFamily: 'system-ui', padding: 24, maxWidth: 720 }}>
      <h1>Perimity — foundation</h1>
      <p>Phase 2. No UI yet; this verifies the API layer against a live backend.</p>

      <h2>auth-service ping</h2>
      {ping.isPending && <p>Checking…</p>}
      {ping.isError && <p>Unreachable: {(ping.error as Error).message}</p>}
      {ping.isSuccess && <pre>{JSON.stringify(ping.data, null, 2)}</pre>}

      <h2>Session</h2>
      <pre>{JSON.stringify({ identity, capabilities }, null, 2)}</pre>

      <h2>Configuration</h2>
      <pre>{JSON.stringify({ config, flags }, null, 2)}</pre>
    </main>
  );
}

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <FoundationCheck />
    </QueryClientProvider>
  );
}
