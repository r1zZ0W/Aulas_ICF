import { lazy } from 'react';
import { BarChart3, Calendar, ClipboardList, Building2, UserRound, Users, Boxes } from 'lucide-react';
import { PRIVATE_ROUTES_META } from './routes.meta';

const ReservationsPage = lazy(() => import('../modules/shared/reservations/ReservationsPage'));
const ClassroomsPage = lazy(() => import('../modules/shared/classrooms/ClassroomsPage'));
const HistoryPage = lazy(() => import('../modules/shared/reservations/HistoryPage'));
const UsersPage = lazy(() => import('../modules/admin/users/UsersPage'));
const ResourcesPage = lazy(() => import('../modules/admin/resources/ResourcesPage'));
const ReportsPage = lazy(() => import('../modules/admin/reports/ReportsPage'));
const ProfilePage = lazy(() => import('../modules/shared/profile/ProfilePage'));

/**
 * Central map of paths to their components and sidebar icons.
 * Kept separate from routeConfig.jsx to avoid module cycles when using 
 * buildParentOptions / buildChildOptions (see classrooms-children-array-request.md §2).
 */
export const ROUTE_COMPONENTS = {
  '/reservations': { element: <ReservationsPage />, icon: <Calendar /> },
  '/classrooms': { element: <ClassroomsPage />, icon: <Building2 /> },
  '/history': { element: <HistoryPage />, icon: <ClipboardList /> },
  '/users': { element: <UsersPage />, icon: <Users /> },
  '/resources': { element: <ResourcesPage />, icon: <Boxes /> },
  '/reports': { element: <ReportsPage />, icon: <BarChart3 /> },
  '/profile': { element: <ProfilePage />, icon: <UserRound /> },
};

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
export const PRIVATE_ROUTES = PRIVATE_ROUTES_META.map((meta) => {
  const config = ROUTE_COMPONENTS[meta.path];
  if (!config) throw new Error(`Missing components for the route: ${meta.path}`);

  return {
    ...meta,
    element: config.element,
    sidebar: meta.sidebar ? { ...meta.sidebar, icon: config.icon } : undefined,
  };
});