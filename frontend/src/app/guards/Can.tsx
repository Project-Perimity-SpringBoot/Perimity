import { useAuth } from '@hooks/useAuth';
import type { Capability } from '@lib/auth/permissions';

/**
 * Renders nothing when the capability is absent — no disabled ghost buttons
 * hinting at features the user cannot reach. This is UX; the server enforces.
 */
export function Can({
  do: capability, children, fallback = null,
}: {
  do: Capability;
  children: React.ReactNode;
  fallback?: React.ReactNode;
}) {
  const { can } = useAuth();
  return <>{can(capability) ? children : fallback}</>;
}
