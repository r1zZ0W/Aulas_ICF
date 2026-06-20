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
 * Returns the set of UUIDs that are ancestors of `uuid`
 * (parent, grandparent, and so on — to any depth).
 *
 * Used by `buildChildOptions` to prevent cycles in the children selector:
 * a classroom cannot be assigned as a child of one of its own ancestors
 * (that would create a circular parent chain).
 *
 * The traversal guards against pre-existing cycles in the data (it won't loop
 * infinitely even if the backend somehow contains a circular reference).
 *
 * @param {string} uuid - The classroom whose ancestors we want.
 * @param {Array<{ uuid: string, linkedRoomUuid?: string | null }>} allClassrooms
 * @returns {Set<string>} set of ancestor UUIDs (excludes `uuid` itself)
 */
export function getAncestorUuids(uuid, allClassrooms) {
  const classroomMap = new Map(allClassrooms.map((c) => [c.uuid, c]));
  const result = new Set();
  let current = classroomMap.get(uuid);
  while (current?.linkedRoomUuid) {
    if (result.has(current.linkedRoomUuid)) break; // guard against existing cycles in DB data
    result.add(current.linkedRoomUuid);
    current = classroomMap.get(current.linkedRoomUuid);
  }
  return result;
}

/**
 * Returns the list of classrooms eligible to be assigned as **children** of `selfUuid`.
 *
 * A classroom is eligible when it is:
 *  1. Active (`isActive = true`)
 *  2. Not the aula being edited (a room can't be its own child)
 *  3. Not an ancestor of `selfUuid` (assigning an ancestor as a child creates a cycle)
 *
 * Note: a classroom that is ALREADY a child of a DIFFERENT parent is still eligible;
 * the PUT on that classroom will reassign its `linkedRoomUuid` to `selfUuid`, taking it
 * from its former parent (the backend allows this as long as no cycle results).
 *
 * @param {Array<{
 *   uuid: string,
 *   name: string,
 *   isActive: boolean,
 *   linkedRoomUuid?: string | null,
 * }>} allClassrooms
 * @param {{ selfUuid?: string | null }} [options]
 * @returns {Array<{ uuid: string, name: string, linkedRoomUuid?: string | null, isActive: boolean }>}
 */
export function buildChildOptions(allClassrooms, { selfUuid = null } = {}) {
  const ancestors = selfUuid ? getAncestorUuids(selfUuid, allClassrooms) : new Set();
  return allClassrooms.filter(
    (c) => c.isActive && c.uuid !== selfUuid && !ancestors.has(c.uuid)
  );
}

// ─────────────────────────────────────────────────────────────────────────────

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
