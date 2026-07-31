import { ROLES } from '../shared/roles';
import CampusDashboard from './CampusDashboard';
import PlatformDashboard from './PlatformDashboard';
import AttendanceView from './AttendanceView';

export const dashboardRoutes = [
  { path: '/today', element: <CampusDashboard />,
    allow: [ROLES.CAMPUS_ADMIN], nav: { label: 'Today', order: 5 } },
  { path: '/platform', element: <PlatformDashboard />,
    allow: [ROLES.SUPER_ADMIN], nav: { label: 'Dashboard', order: 5 } },
  { path: '/events/:id/attendance', element: <AttendanceView />,
    allow: [ROLES.CAMPUS_ADMIN, ROLES.FACULTY, ROLES.SUPER_ADMIN] },
];
