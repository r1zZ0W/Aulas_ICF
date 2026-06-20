/**
 * @fileoverview Pure helpers for semester status derivation and display.
 *
 * `isActive` from the API is a backend-derived boolean (today inside [startDate, endDate]).
 * `deriveSemesterStatus` adds the "Futuro" distinction that the backend doesn't expose,
 * using `startDate > today` when `isActive` is false.
 */

/**
 * Derives the display status of a semester from its response object.
 * The backend already computes `isActive`; we only need to distinguish
 * future semesters (not yet started) from concluded ones (already ended).
 *
 * @param {{ isActive: boolean, startDate: string, endDate: string }} semester
 * @returns {'active' | 'future' | 'concluded'}
 */
export function deriveSemesterStatus(semester) {
  if (semester.isActive) return 'active';
  const today = new Date().toISOString().slice(0, 10);
  if (semester.startDate > today) return 'future';
  return 'concluded';
}

/** Human-readable label for each semester status. */
export const SEMESTER_STATUS_LABEL = {
  active:    'Activo',
  future:    'Futuro',
  concluded: 'Concluido',
};

/** Badge variant for each semester status (matches Badge component variants). */
export const SEMESTER_STATUS_BADGE_VARIANT = {
  active:    'success',
  future:    'primary',
  concluded: 'neutral',
};
