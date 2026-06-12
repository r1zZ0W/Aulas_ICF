/**
 * Role constants matching the values returned by the backend (LoginResponseDTO.role).
 * Add new roles here — all role checks downstream consume this map.
 */
export const ROLES = {
  ADMIN: 'admin',
  MAESTRO: 'teacher',
};

/**
 * Maps a role to its default landing route after login.
 * Returns '/login' for unknown roles so callers can always navigate safely.
 *
 * @param {string} role
 * @returns {string}
 */
export function getDashboardRoute(role) {
  const routes = {
    [ROLES.ADMIN]: '/dashboard',
    [ROLES.MAESTRO]: '/dashboard',
  };
  return routes[role] ?? '/login';
}
