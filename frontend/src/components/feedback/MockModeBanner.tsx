import { isMocked } from '@lib/mocks/mockAdapter';
import type { ServiceName } from '@lib/api/serviceName';

const SERVICES: readonly ServiceName[] = ['auth', 'user', 'gatepass', 'campus', 'guard', 'qr'];

/**
 * A loud, permanent strip whenever any service is mocked.
 *
 * With mocks on, any email and any password sign you in. That is correct
 * behaviour for building a screen against no backend, and it is indistinguishable
 * from a broken login if nobody told you. This tells you, on every screen, and
 * it cannot be dismissed — a dismissible warning about fake authentication is a
 * warning nobody sees twice.
 *
 * Renders nothing in production builds and nothing when every service is live,
 * so it costs nothing once the mocks are off.
 */
export function MockModeBanner() {
  if (!import.meta.env.DEV) return null;

  const mocked = SERVICES.filter(isMocked);
  if (mocked.length === 0) return null;

  const all = mocked.length === SERVICES.length;

  return (
    <div
      role="status"
      className="flex flex-wrap items-center justify-center gap-[var(--sp-2)] bg-[var(--review-bg)] px-[var(--sp-3)] py-[var(--sp-1)] text-center"
    >
      <span className="text-caption font-semibold text-[var(--review-fg)]">
        MOCK DATA
      </span>
      <span className="text-caption text-[var(--review-fg)]">
        {all ? 'All six services' : mocked.join(', ')} returning fixtures — any email and
        password will sign you in. Set <code className="text-mono">VITE_USE_MOCKS=false</code> in{' '}
        <code className="text-mono">.env.local</code> to use the real backend.
      </span>
    </div>
  );
}
