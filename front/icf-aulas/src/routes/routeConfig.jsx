import { ROLES } from '../utils/roles';

import Dashboard from '../modules/shared/dashboard/Dashboard';
import ClassroomsPage from '../modules/shared/classrooms/ClassroomsPage';
import ReservationsPage from '../modules/shared/reservations/ReservationsPage';
import UsersPage from '../modules/admin/users/UsersPage';

/**
 * Central declaration of every private route.
 *
 * Each entry is the single source of truth for three concerns:
 *   - Routing: `path` and `element` are consumed by PrivateRouter.
 *   - Authorization: `allowedRoles` is enforced by RoleGuard.
 *   - Navigation: `sidebar` metadata is read by the Sidebar component.
 *
 * To add a new route: append an entry here — no other file needs to change.
 *
 * @type {Array<{
 *   path: string,
 *   element: JSX.Element,
 *   allowedRoles: string[],
 *   sidebar: { label: string, icon: string, show: boolean }
 * }>}
 */
export const PRIVATE_ROUTES = [
  {
    path: '/dashboard',
    element: <Dashboard />,
    allowedRoles: [ROLES.ADMIN, ROLES.MAESTRO],
    sidebar: { label: 'Dashboard', icon: 'bi-speedometer2', show: true },
  },
  {
    path: '/classrooms',
    element: <ClassroomsPage />,
    allowedRoles: [ROLES.ADMIN, ROLES.MAESTRO],
    sidebar: { label: 'Aulas', icon: 'bi-building', show: true },
  },
  {
    path: '/reservations',
    element: <ReservationsPage />,
    allowedRoles: [ROLES.ADMIN, ROLES.MAESTRO],
    sidebar: { label: 'Reservaciones', icon: 'bi-calendar-check', show: true },
  },
  {
    path: '/users',
    element: <UsersPage />,
    allowedRoles: [ROLES.ADMIN],
    sidebar: { label: 'Usuarios', icon: 'bi-people', show: true },
  },
];
