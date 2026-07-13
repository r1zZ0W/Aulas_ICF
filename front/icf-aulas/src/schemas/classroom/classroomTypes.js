/**
 * @fileoverview Classroom types map and helpers.
 * Serves as the single source of truth for all classroom type-related logic.
 */

/**
 * Maps ClassroomType enum values (backend keys) to Spanish display labels.
 * @type {Record<string, string>}
 */
export const CLASSROOM_TYPES_MAP = {
  AULA: 'Aula',
  AUDITORIO: 'Auditorio',
  LABORATORIO: 'Laboratorio',
  SALA_SEMINARIOS: 'Sala de seminarios',
};

/**
 * Array of { value, label } pairs ready for UI Select dropdown components.
 * @type {Array<{value: string, label: string}>}
 */
export const CLASSROOM_TYPES = Object.entries(CLASSROOM_TYPES_MAP).map(
  ([value, label]) => ({ value, label })
);

/**
 * Returns the Spanish display label for a ClassroomType key.
 * Falls back to the key itself or a dash if nullish.
 * @param {string} type - ClassroomType enum value
 * @returns {string} Spanish label
 */
export const typeLabel = (type) => CLASSROOM_TYPES_MAP[type] ?? type ?? '—';
