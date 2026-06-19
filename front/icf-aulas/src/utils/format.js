/**
 * @fileoverview Generic presentation-layer helpers.
 */

/**
 * Returns up to 2 initials from a user's firstName and lastNames.
 * @param {{ firstName?: string, lastNames?: string }} user
 * @returns {string}
 */
export function getInitials(user) {
  const parts = [user.firstName, user.lastNames].filter(Boolean);
  return parts.map((p) => p[0]).join('').toUpperCase().slice(0, 2);
}
