import { ROLES } from '../shared/roles';
import StudentDashboard from './StudentDashboard';
import PassDetail from './PassDetail';
import PassHistory from './PassHistory';
import EntryHistory from './EntryHistory';
import Profile from './Profile';
import Documents from './Documents';

const S = [ROLES.STUDENT];

export const studentRoutes = [
  { path: '/student', element: <StudentDashboard />, allow: S, nav: { label: 'Home', order: 10 } },
  { path: '/student/pass', element: <PassDetail />, allow: S, nav: { label: 'My pass', order: 20 } },
  // Same component, paused state — reachable for review and for the demo.
  { path: '/student/pass/paused', element: <PassDetail paused />, allow: S },
  { path: '/student/passes', element: <PassHistory />, allow: S, nav: { label: 'Pass history', order: 30 } },
  { path: '/student/entries', element: <EntryHistory />, allow: S, nav: { label: 'Entries', order: 40 } },
  { path: '/student/profile', element: <Profile />, allow: S, nav: { label: 'Profile', order: 50 } },
  { path: '/student/profile/edit', element: <Profile editable />, allow: S },
  { path: '/student/documents', element: <Documents />, allow: S, nav: { label: 'Documents', order: 60 } },
];
