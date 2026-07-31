import { ROLES } from '../shared/roles';
import VisitorDashboard from './VisitorDashboard';
import ApplyForPass from './ApplyForPass';
import RequestStatus from './RequestStatus';

export const visitorRoutes = [
  { path: '/visitor', element: <VisitorDashboard hasPass />, allow: [ROLES.VISITOR],
    nav: { label: 'My pass', order: 10 } },
  { path: '/visitor/apply', element: <ApplyForPass />, allow: [ROLES.VISITOR] },
  { path: '/visitor/submitted', element: <RequestStatus mode="submitted" />, allow: [ROLES.VISITOR] },
  { path: '/visitor/rejected', element: <RequestStatus mode="rejected" />, allow: [ROLES.VISITOR] },
];
