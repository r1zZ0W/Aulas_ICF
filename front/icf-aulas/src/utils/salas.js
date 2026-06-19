/**
 * @fileoverview Classroom display helpers — colors and shape for the Sidebar / calendar.
 *
 * The single source of truth for classroom data is the backend
 * (GET /api/v1/classrooms). This file provides:
 *
 *   - {@link ROOM_COLORS} — fixed palette for visual identification.
 *   - {@link roomColor} — deterministic color derived from a classroom UUID via
 *     a simple integer hash, so each classroom always gets the same color
 *     regardless of list order, page, or total number of classrooms.
 *     Future: once the backend exposes a `color` field, use
 *     `classroom.color ?? roomColor(classroom.uuid)` for zero-migration fallback.
 *   - {@link buildRoomsFromClassrooms} — maps API response items to the internal
 *     room shape used by Sidebar, ReservaModal, and ReservationContext.
 */

// ── Color palette ─────────────────────────────────────────────────────────────
/**
 * Fixed color palette for classroom visual identification.
 * Order is irrelevant — color is assigned by UUID hash, not by list index.
 *
 * @type {string[]}
 */
export const ROOM_COLORS = [
  '#2563eb', // blue
  '#16a34a', // green
  '#9333ea', // purple
  '#ea580c', // orange
  '#f59e0b', // amber
  '#0891b2', // cyan
  '#db2777', // pink
  '#65a30d', // lime
];

/**
 * Returns a stable, deterministic hex color string for a given classroom UUID.
 *
 * Uses a djb2-style hash over the UUID characters; the result is clamped to
 * the {@link ROOM_COLORS} palette via modulo, so it never depends on list
 * order or page offset.
 *
 * Future: once the backend exposes a `color` field, prefer
 * `classroom.color ?? roomColor(classroom.uuid)`.
 *
 * @param {string} uuid - Classroom public UUID.
 * @returns {string} Hex color string from {@link ROOM_COLORS}.
 */
export function roomColor(uuid) {
  if (!uuid) return ROOM_COLORS[0];
  let hash = 0;
  for (let i = 0; i < uuid.length; i++) {
    hash = ((hash * 31) + uuid.charCodeAt(i)) >>> 0; // keep as unsigned 32-bit int
  }
  return ROOM_COLORS[hash % ROOM_COLORS.length];
}

/**
 * Converts a list of ClassroomResponseDTO items (from the API) into the
 * internal room shape consumed by Sidebar, ReservaModal, ReasignarModal,
 * and ReservaInfoModal.
 *
 * Inactive classrooms are excluded — only active rooms are displayed in the
 * calendar and the reservation flow.
 *
 * @param {Array<{
 *   uuid:     string,
 *   name:     string,
 *   capacity: number,
 *   type:     string,
 *   isActive: boolean,
 * }>} classrooms - Raw API classroom list.
 * @returns {Array<{
 *   uuid:     string,
 *   label:    string,
 *   color:    string,
 *   capacity: number,
 *   type:     string,
 * }>} Room display objects.
 */
export function buildRoomsFromClassrooms(classrooms = []) {
  return classrooms
    .filter(c => c.isActive)
    .map(c => ({
      uuid:     c.uuid,
      label:    c.name,
      // Future: `c.color ?? roomColor(c.uuid)` once backend ships the color field.
      color:    roomColor(c.uuid),
      capacity: c.capacity,
      type:     c.type,
    }));
}
