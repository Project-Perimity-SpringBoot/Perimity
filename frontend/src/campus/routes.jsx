import { ROLES } from '../shared/roles';
import CampusOverview from './CampusOverview';
import UserManagement from './UserManagement';
import Departments from './Departments';
import Gates from './Gates';
import Blocklist from './Blocklist';
import CampusPolicy from './CampusPolicy';
import AuditLog from './AuditLog';

const A = [ROLES.CAMPUS_ADMIN];

/**
 * There is NO "Campuses" entry here. A Campus Admin manages exactly one campus;
 * campuses belong to the Super Admin console.
 */
export const campusRoutes = [
  { path: '/campus', element: <CampusOverview />, allow: A, nav: { label: 'Overview', order: 10 } },
  { path: '/campus/users', element: <UserManagement />, allow: A, nav: { label: 'Users', order: 30 } },
  { path: '/campus/departments', element: <Departments />, allow: A, nav: { label: 'Departments', order: 40 } },
  { path: '/campus/gates', element: <Gates />, allow: A, nav: { label: 'Gates', order: 50 } },
  { path: '/campus/blocklist', element: <Blocklist />, allow: A, nav: { label: 'Blocklist', order: 60 } },
  { path: '/campus/policy', element: <CampusPolicy />, allow: A, nav: { label: 'Policy', order: 70 } },
  { path: '/campus/audit', element: <AuditLog />, allow: A, nav: { label: 'Audit log', order: 80 } },
];
