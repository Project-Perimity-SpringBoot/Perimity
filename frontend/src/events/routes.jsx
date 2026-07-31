import { ROLES } from '../shared/roles';
import EventList from './EventList';
import EventForm from './EventForm';
import EventDetail from './EventDetail';

const A = [ROLES.CAMPUS_ADMIN, ROLES.FACULTY, ROLES.SUPER_ADMIN];

export const eventRoutes = [
  { path: '/events', element: <EventList />, allow: A, nav: { label: 'Events', order: 22 } },
  { path: '/events/new', element: <EventForm />, allow: A },
  { path: '/events/:id', element: <EventDetail />, allow: A },
  { path: '/events/:id/edit', element: <EventForm />, allow: A },
];
