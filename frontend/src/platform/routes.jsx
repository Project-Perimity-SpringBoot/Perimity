import { ROLES } from '../shared/roles';
import PlatformOverview from './PlatformOverview';
import Campuses from './Campuses';
import CampusAdmins from './CampusAdmins';
import AuditLog from '../campus/AuditLog';

const SA = [ROLES.SUPER_ADMIN];

export const platformRoutes = [
  { path: '/admin', element: <PlatformOverview />, allow: SA, nav: { label: 'Platform', order: 10 } },
  { path: '/admin/campuses', element: <Campuses />, allow: SA, nav: { label: 'Campuses', order: 20 } },
  { path: '/admin/admins', element: <CampusAdmins />, allow: SA, nav: { label: 'Campus admins', order: 30 } },
  // Same component, platform scope. The audit pattern is identical across
  // scopes; only the filter set differs.
  { path: '/admin/audit', element: <AuditLog scope="platform" />, allow: SA, nav: { label: 'Platform audit', order: 40 } },
];
