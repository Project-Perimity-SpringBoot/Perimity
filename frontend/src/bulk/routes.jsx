import { ROLES } from '../shared/roles';
import BulkUpload from './BulkUpload';
import BulkReview from './BulkReview';
import BulkProgress from './BulkProgress';
import BulkHistory from './BulkHistory';

const A = [ROLES.CAMPUS_ADMIN, ROLES.FACULTY, ROLES.SUPER_ADMIN];

export const bulkRoutes = [
  { path: '/bulk', element: <BulkUpload />, allow: A, nav: { label: 'Bulk upload', order: 25 } },
  // Deep screens, reached from the flow. Not nav items.
  { path: '/bulk/history', element: <BulkHistory />, allow: A },
  { path: '/bulk/:batchId/review', element: <BulkReview />, allow: A },
  { path: '/bulk/:batchId/progress', element: <BulkProgress />, allow: A },
];
