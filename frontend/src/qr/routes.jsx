import { ROLES } from '../shared/roles';
import PassDownload from './PassDownload';

export const qrRoutes = [
  // Reached from a pass card, not from the sidebar — no `nav` key.
  { path: '/pass/:id/download', element: <PassDownload />,
    allow: [ROLES.STUDENT, ROLES.FACULTY, ROLES.VISITOR] },
];
