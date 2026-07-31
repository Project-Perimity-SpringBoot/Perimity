import { ROLES } from '../shared/roles';
import ApprovalQueue from './ApprovalQueue';

/**
 * Campus Admin and Faculty see the SAME queue component. The difference is
 * scope, and scope comes from the token, not from a second screen — building
 * two near-identical approval screens is how the two drift apart.
 */
export const gatepassRoutes = [
  { path: '/approvals', element: <ApprovalQueue />,
    allow: [ROLES.FACULTY, ROLES.CAMPUS_ADMIN],
    nav: { label: 'Approvals', order: 20 } },
];
