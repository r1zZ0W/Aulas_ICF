import { ROLES } from '../utils/roles';

/**
 * Pure route metadata: paths, role authorization, and sidebar labels.
 *
 * Deliberately free of React/component imports so files that only need to
 * know "which paths exist" (e.g. PublicRouter, to detect access attempts to
 * private routes) don't pull in lazy-loaded pages or icon components.
 * Component wiring lives in `routeConfig.jsx`, which composes on top of this.
 *
 * @type {Array<{
 *   path: string,
 *   allowedRoles: string[],
 *   sidebar: { label: string, show: boolean }
 * }>}
 */
export const PRIVATE_ROUTES_META = [
  {
    path: '/reservations',
    allowedRoles: [ROLES.ADMIN, ROLES.MAESTRO],
    sidebar: { label: 'Reservaciones', show: true },
  },
  {
    path: '/classrooms',
    allowedRoles: [ROLES.ADMIN, ROLES.MAESTRO],
    sidebar: { label: 'Aulas', show: true },
  },
  {
    path: '/history',
    allowedRoles: [ROLES.ADMIN, ROLES.MAESTRO],
    sidebar: { label: 'Historial', show: true },
  },
  {
    path: '/users',
    allowedRoles: [ROLES.ADMIN],
    sidebar: { label: 'Usuarios', show: true },
  },
  {
    path: '/reports',
    allowedRoles: [ROLES.ADMIN],
    sidebar: { label: 'Reportes y Estadísticas', show: true },
  },
  {
    path: '/profile',
    allowedRoles: [ROLES.ADMIN, ROLES.MAESTRO],
    sidebar: { label: 'Mi perfil', show: true },
  },
];

/** Bare list of private paths, for consumers that only need path strings. */
export const PRIVATE_PATHS = PRIVATE_ROUTES_META.map((r) => r.path);
