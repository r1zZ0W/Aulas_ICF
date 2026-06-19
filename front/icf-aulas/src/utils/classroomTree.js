/**
 * @fileoverview Pure helpers for classroom parent/child tree operations.
 *
 * Used by ClassroomsPage and ClassroomInfoModal to compute parent options
 * and direct children without mutating state.
 *
 * All functions are side-effect-free and safe to call inside useMemo.
 */

/**
 * Returns the set of UUIDs that are descendants of `uuid`
 * (children, grandchildren, and so on — to any depth).
 *
 * Used by `buildParentOptions` to prevent cycles in the parent selector:
 * a classroom cannot be assigned to one of its own descendants as a parent
 * (the "Aulas Matrioshka" problem).
 *
 * The BFS guards against pre-existing cycles in the data (it won't loop
 * infinitely even if the backend somehow contains a circular reference).
 *
 * @param {string} uuid - The classroom whose descendants we want.
 * @param {Array<{ uuid: string, linkedRoomUuid?: string | null }>} allClassrooms
 * @returns {Set<string>} set of descendant UUIDs (excludes `uuid` itself)
 */
export function getDescendantUuids(uuid, allClassrooms) {
  // Build parentUuid → [childUuid, ...] map in one pass
  /** @type {Map<string, string[]>} */
  const childrenMap = new Map();
  for (const c of allClassrooms) {
    if (!c.linkedRoomUuid) continue;
    const children = childrenMap.get(c.linkedRoomUuid) ?? [];
    children.push(c.uuid);
    childrenMap.set(c.linkedRoomUuid, children);
  }

  // BFS from uuid
  const result = new Set();
  const queue = [...(childrenMap.get(uuid) ?? [])];
  while (queue.length > 0) {
    const child = queue.shift();
    if (result.has(child)) continue; // guard against existing cycles in DB data
    result.add(child);
    queue.push(...(childrenMap.get(child) ?? []));
  }
  return result;
}

/**
 * Returns the direct children of `uuid` from the flat classroom list.
 *
 * @param {string} uuid
 * @param {Array<{ uuid: string, linkedRoomUuid?: string | null, name: string }>} allClassrooms
 * @returns {Array<{ uuid: string, name: string }>}
 */
export function getChildren(uuid, allClassrooms) {
  if (!uuid) return [];
  return allClassrooms.filter(c => c.linkedRoomUuid === uuid);
}

/**
 * Builds the `{value, label}` option list for the "Aula padre" selector in the
 * classroom create/edit form.
 *
 * Exclusion rules (preventing cycles):
 *  1. The classroom being edited (`excludeUuid`) — a room can't be its own parent.
 *  2. All descendants of `excludeUuid` — a room can't point to a descendant as parent.
 *  3. Inactive classrooms — a deactivated room should not be offered as a parent.
 *
 * Always prepends a "none" option as the first entry.
 *
 * @param {Array<{
 *   uuid: string,
 *   name: string,
 *   isActive: boolean,
 *   linkedRoomUuid?: string | null,
 * }>} allClassrooms
 * @param {{ excludeUuid?: string | null }} [options]
 * @returns {Array<{ value: string, label: string }>}
 */
export function buildParentOptions(allClassrooms, { excludeUuid = null } = {}) {
  const descendants = excludeUuid
    ? getDescendantUuids(excludeUuid, allClassrooms)
    : new Set();

  const eligible = allClassrooms.filter(c =>
    c.isActive &&
    c.uuid !== excludeUuid &&
    !descendants.has(c.uuid)
  );

  return [
    { value: '', label: 'Ninguna (aula independiente)' },
    ...eligible.map(c => ({ value: c.uuid, label: c.name })),
  ];
}
