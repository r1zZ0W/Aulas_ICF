import { ROLES } from '../utils/roles';

import ClassroomsPage from '../modules/shared/classrooms/ClassroomsPage';
import ReservationsPage from '../modules/shared/reservations/ReservationsPage';
import ProfilePage from '../modules/shared/profile/ProfilePage';
import UsersPage from '../modules/admin/users/UsersPage';
import { Calendar, School, UserRound, Users } from 'lucide-react';

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
    path: '/reservations',
    element: <ReservationsPage />,
    allowedRoles: [ROLES.ADMIN, ROLES.MAESTRO],
    sidebar: { label: 'Reservaciones', icon: <Calendar />, show: true },
  },
  {
    path: '/profile',
    element: <ProfilePage />,
    allowedRoles: [ROLES.ADMIN, ROLES.MAESTRO],
    sidebar: { label: 'Mi perfil', icon: <UserRound />, show: true },
  },
  {
    path: '/classrooms',
    element: <ClassroomsPage />,
    allowedRoles: [ROLES.ADMIN, ROLES.MAESTRO],
    sidebar: { label: 'Aulas', icon: <School />, show: true },
  },
  {
    path: '/users',
    element: <UsersPage />,
    allowedRoles: [ROLES.ADMIN],
    sidebar: { label: 'Usuarios', icon: <Users />, show: true },
  }
];
