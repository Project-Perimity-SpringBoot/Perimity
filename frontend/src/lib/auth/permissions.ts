import type { Role } from '@/types/enums';
import { isStaff } from '@/types/enums';
import type { Identity } from './claims';

/**
 * One place where role becomes capability, so no component writes
 * `role === 'FACULTY'`. Every entry is traceable to a @PreAuthorize or a
 * SecurityConfig matcher in the backend — see Frontend Contract §3.2.
 */
export type Capability =
  | 'pass:issue' | 'pass:changeStatus' | 'pass:viewAny' | 'pass:count'
  | 'request:decide' | 'request:viewQueue' | 'request:lookupByEmail'
  | 'event:manage' | 'event:viewAttendance'
  | 'bulk:run'
  | 'user:create' | 'user:list' | 'user:changeStatus'
  | 'department:manage' | 'gate:manage'
  | 'campus:create' | 'campus:edit' | 'campus:changeStatus' | 'campus:viewStats'
  | 'config:manage' | 'blocklist:manage' | 'audit:view'
  | 'profile:createStudent' | 'profile:createFaculty' | 'profile:listDirectory'
  | 'document:verify'
  | 'scan:perform' | 'session:manage' | 'session:supervise'
  | 'entrylog:view'
  | 'platform:view';

/** Capabilities that hit an endpoint calling CurrentUser.campusId(). */
const CAMPUS_SCOPED: ReadonlySet<Capability> = new Set<Capability>([
  'pass:issue', 'pass:changeStatus', 'pass:viewAny', 'pass:count',
  'request:decide', 'request:viewQueue', 'request:lookupByEmail',
  'event:manage', 'bulk:run',
  'user:list', 'blocklist:manage', 'audit:view',
  'entrylog:view',
]);

const BY_ROLE: Readonly<Record<Role, readonly Capability[]>> = {
  SUPER_ADMIN: [
    // Reachable despite having no campus.
    'campus:create', 'campus:edit', 'campus:changeStatus', 'campus:viewStats',
    'gate:manage', 'config:manage', 'department:manage',
    'user:create', 'user:changeStatus',
    'profile:createStudent', 'profile:createFaculty', 'profile:listDirectory',
    'document:verify', 'session:supervise', 'platform:view',
    // Annotated for SUPER_ADMIN but 403 at runtime — filtered out below by
    // CAMPUS_SCOPED because campusId is null. Listed so the intent is visible.
    'pass:issue', 'pass:changeStatus', 'pass:viewAny', 'pass:count',
    'request:decide', 'request:viewQueue', 'request:lookupByEmail',
    'event:manage', 'event:viewAttendance', 'bulk:run',
    'user:list', 'blocklist:manage', 'audit:view', 'entrylog:view',
  ],
  CAMPUS_ADMIN: [
    'pass:issue', 'pass:changeStatus', 'pass:viewAny', 'pass:count',
    'request:decide', 'request:viewQueue', 'request:lookupByEmail',
    'event:manage', 'event:viewAttendance', 'bulk:run',
    'user:create', 'user:list', 'user:changeStatus',
    'department:manage', 'gate:manage', 'campus:edit',
    'config:manage', 'blocklist:manage', 'audit:view',
    'profile:createStudent', 'profile:createFaculty', 'profile:listDirectory',
    'document:verify', 'session:supervise', 'entrylog:view',
  ],
  FACULTY: [
    'pass:issue', 'pass:changeStatus', 'pass:viewAny', 'pass:count',
    'request:decide', 'request:viewQueue', 'request:lookupByEmail',
    'event:manage', 'event:viewAttendance', 'bulk:run',
    'user:list', 'profile:createStudent', 'profile:listDirectory',
    // NOT blocklist:manage — BlocklistController is SA/CA only (blocker B10).
    // NOT entrylog:view  — the matcher excludes FACULTY except for attendance.
  ],
  STUDENT: [],
  VISITOR: [],
  // GUARD is deliberately not staff. scan:perform admits nobody else — an
  // admin scanning would create an entry log with no shift behind it.
  GUARD: ['scan:perform', 'session:manage', 'entrylog:view'],
};

export function capabilitiesFor(
  user: Pick<Identity, 'role' | 'campusId'> | null,
): ReadonlySet<Capability> {
  if (!user) return new Set();
  const granted = BY_ROLE[user.role] ?? [];
  if (user.campusId !== null) return new Set(granted);

  // Blocker B5, encoded once instead of discovered as a 403 on twelve screens.
  return new Set(granted.filter((c) => !CAMPUS_SCOPED.has(c)));
}

export function can(
  user: Pick<Identity, 'role' | 'campusId'> | null,
  capability: Capability,
): boolean {
  return capabilitiesFor(user).has(capability);
}

/**
 * Mirrors CurrentUser.requireSelfOrStaff. Used to decide whether to render a
 * link, never to decide whether data may be shown — the server does that.
 */
export function canReadUserScopedResource(
  user: Pick<Identity, 'role' | 'userId'> | null,
  targetUserId: number,
): boolean {
  if (!user) return false;
  return isStaff(user.role) || user.userId === targetUserId;
}

/** Where each role lands after sign-in. */
export const LANDING_ROUTE: Readonly<Record<Role, string>> = {
  SUPER_ADMIN: '/platform',
  CAMPUS_ADMIN: '/admin',
  FACULTY: '/faculty',
  STUDENT: '/student',
  VISITOR: '/visitor',
  GUARD: '/guard',
};
