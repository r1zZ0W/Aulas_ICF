/**
 * Role constants matching the values returned by the backend (LoginResponseDTO.role).
 * Add new roles here — all role checks downstream consume this map.
 */
export const ROLES = {
  ADMIN: 'admin',
  MAESTRO: 'teacher',
};

/**
 * Maps backend roles to display names.
 *
 * @type {Record<string, string>}
 */
export const DISPLAYED_ROLES = {
  [ROLES.ADMIN]: 'Administrador',
  [ROLES.MAESTRO]: 'Maestro',
}

/**
 * Maps a role to its default landing route after login.
 * Returns '/login' for unknown roles so callers can always navigate safely.
 *
 * @param {string} role
 * @returns {string}
 */
export function getDashboardRoute(role) {
  const routes = {
    [ROLES.ADMIN]: '/reservations',
    [ROLES.MAESTRO]: '/reservations',
  };
  return routes[role] ?? '/login';
}
