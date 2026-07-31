/**
 * THE FILE THAT STOPS SIX PEOPLE FIGHTING OVER App.jsx.
 *
 * Every folder exports one array. This file collects them and hands them to
 * App.jsx, which then never changes again.
 *
 * WHY THIS EXISTS
 * The obvious shape — everyone adds a <Route> to App.jsx and a line to a NAV
 * array in Layout.jsx — means six people edit the same two files for the whole
 * of Days 14 to 19. Every pull is a conflict, in a file where a bad resolution
 * silently deletes somebody's screen rather than failing loudly.
 *
 * Here, adding a screen touches ONE file, inside ONE folder.
 *
 * Each entry:
 *   path      route path, unique across the app
 *   element   the component
 *   allow     roles permitted (omit for any signed-in user)
 *   nav       optional — { label, order } puts it in the sidebar;
 *             omit for detail screens that should not be nav items
 */
import { authRoutes, authFullBleedRoutes, publicAuthRoutes } from '../auth/routes';
import { studentRoutes }  from '../student/routes';
import { visitorRoutes }  from '../visitor/routes';
import { gatepassRoutes } from '../gatepass/routes';
import { campusRoutes }   from '../campus/routes';
import { platformRoutes } from '../platform/routes';
import { guardRoutes, guardFullBleedRoutes } from '../guard/routes';
import { bulkRoutes }      from '../bulk/routes';
import { eventRoutes }     from '../events/routes';
import { dashboardRoutes } from '../dashboard/routes';
import { qrRoutes }       from '../qr/routes';

/** Screens rendered inside the app shell. */
export const featureRoutes = [
  ...authRoutes,
  ...studentRoutes,
  ...visitorRoutes,
  ...gatepassRoutes,
  ...campusRoutes,
  ...platformRoutes,
  ...guardRoutes,
  ...qrRoutes,
  ...bulkRoutes,
  ...eventRoutes,
  ...dashboardRoutes,
];

/** Signed-in screens rendered WITHOUT the shell — the guard app, chiefly. */
export const fullBleedRoutes = [
  ...authFullBleedRoutes,
  ...guardFullBleedRoutes,
];

/** Public screens, no auth gate at all. */
export { publicAuthRoutes };

/** Sidebar entries, derived — so a screen and its nav item can never disagree. */
export const navItems = featureRoutes
  .filter((r) => r.nav)
  .sort((a, b) => (a.nav.order ?? 99) - (b.nav.order ?? 99))
  .map((r) => ({ to: r.path, label: r.nav.label, roles: r.allow }));

/**
 * Duplicate-path guard. Two owners claiming the same path is a real risk with
 * six folders, and React Router resolves it by silently picking one. Failing
 * loudly in dev is worth the six lines.
 */
if (import.meta.env?.DEV) {
  const seen = new Set();
  for (const r of [...featureRoutes, ...fullBleedRoutes, ...publicAuthRoutes]) {
    if (seen.has(r.path)) console.error(`[routes] duplicate path: ${r.path}`);
    seen.add(r.path);
  }
}
