import { lazy } from 'react';
import { BarChart3, Calendar, ClipboardList, Building2, UserRound, Users } from 'lucide-react';
import { PRIVATE_ROUTES_META } from './routes.meta';

const ReservationsPage = lazy(() => import('../modules/shared/reservations/ReservationsPage'));
const ClassroomsPage = lazy(() => import('../modules/shared/classrooms/ClassroomsPage'));
const HistoryPage = lazy(() => import('../modules/shared/reservations/HistoryPage'));
const UsersPage = lazy(() => import('../modules/admin/users/UsersPage'));
const ReportsPage = lazy(() => import('../modules/admin/reports/ReportsPage'));
const ProfilePage = lazy(() => import('../modules/shared/profile/ProfilePage'));

const metaFor = (path) => PRIVATE_ROUTES_META.find((m) => m.path === path);

/**
 * Central declaration of every private route.
 *
 * Each entry is the single source of truth for three concerns:
 *   - Routing: `path` and `element` are consumed by PrivateRouter.
 *   - Authorization: `allowedRoles` is enforced by RoleGuard.
 *   - Navigation: `sidebar` metadata is read by the Sidebar component.
 *
 * Path/roles/labels come from `routes.meta.js`; this file only adds the
 * lazy-loaded element and icon for each one. To add a new route: append an
 * entry to `routes.meta.js` and its element/icon here.
 * Path/roles/labels come from `routes.meta.js`; this file only adds the
 * lazy-loaded element and icon for each one. To add a new route: append an
 * entry to `routes.meta.js` and its element/icon here.
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
    ...metaFor('/reservations'),
    ...metaFor('/reservations'),
    element: <ReservationsPage />,
    sidebar: { ...metaFor('/reservations').sidebar, icon: <Calendar /> },
    sidebar: { ...metaFor('/reservations').sidebar, icon: <Calendar /> },
  },
  {
    ...metaFor('/classrooms'),
    ...metaFor('/classrooms'),
    element: <ClassroomsPage />,
    sidebar: { ...metaFor('/classrooms').sidebar, icon: <Building2 /> },
  },
  {
    ...metaFor('/history'),
    ...metaFor('/history'),
    element: <HistoryPage />,
    sidebar: { ...metaFor('/history').sidebar, icon: <ClipboardList /> },
    sidebar: { ...metaFor('/history').sidebar, icon: <ClipboardList /> },
  },
  {
    ...metaFor('/users'),
    ...metaFor('/users'),
    element: <UsersPage />,
    sidebar: { ...metaFor('/users').sidebar, icon: <Users /> },
  },
  {
    ...metaFor('/reports'),
    ...metaFor('/reports'),
    element: <ReportsPage />,
    sidebar: { ...metaFor('/reports').sidebar, icon: <BarChart3 /> },
    sidebar: { ...metaFor('/reports').sidebar, icon: <BarChart3 /> },
  },
  {
    ...metaFor('/profile'),
    ...metaFor('/profile'),
    element: <ProfilePage />,
    sidebar: { ...metaFor('/profile').sidebar, icon: <UserRound /> },
    sidebar: { ...metaFor('/profile').sidebar, icon: <UserRound /> },
  },
];
