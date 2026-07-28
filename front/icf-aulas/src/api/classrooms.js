import { createApiClient } from './base.js';
import {
  ClassroomResponseSchema,
  ClassroomStatsSchema,
} from '../schemas/classroom.js';
import { PagedResultSchema } from '../schemas/pagedResult.js';
import { buildPageParams } from '../utils/queryUtils.js';
import { resolveApiError } from '../errors/resolveApiError.js';
import { ApiError } from '../errors/ApiError.js';

const api = createApiClient();

/**
 * Retrieves a paginated list of classrooms.
 * The server automatically returns all (active + inactive) for ADMIN and only
 * active ones for TEACHER — no client-side filtering needed.
 * The `search` param is forwarded but currently a no-op on the backend
 * (see back/aulas/docs/classrooms-frontend-requests.md for the pending request).
 *
 * GET /api/v1/classrooms[?search=&page=&size=&sort=&direction=]
 */
export async function getClassrooms({ search, page, size, sort, direction } = {}) {
  const qs = buildPageParams({ search, page, size, sort, direction });
  return api.getValidated(`/api/v1/classrooms${qs}`, { schema: PagedResultSchema(ClassroomResponseSchema) });
}

/**
 * Retrieves a single classroom by its public UUID.
 * GET /api/v1/classrooms/{uuid}
 */
export async function getClassroom(uuid) {
  return api.getValidated(`/api/v1/classrooms/${uuid}`, { schema: ClassroomResponseSchema });
}

/**
 * Retrieves aggregated classroom statistics. ADMIN only.
 * GET /api/v1/classrooms/stats
 *
 * Returns null when the endpoint is not yet implemented on the backend (404).
 * Once the backend ships GET /api/v1/classrooms/stats, this function will
 * transparently start returning real data.
 */
export async function getClassroomStats() {
  try {
    return await api.getValidated('/api/v1/classrooms/stats', { schema: ClassroomStatsSchema });
  } catch (error) {
    // 404 means the backend hasn't implemented the endpoint yet — return null
    // so the UI can show a graceful placeholder instead of an error toast.
    if (error instanceof ApiError && error.status === 404) return null;
    throw resolveApiError(error);
  }
}

/**
 * Creates a new classroom. ADMIN only.
 * POST /api/v1/classrooms
 *
 * `payload` is not re-validated here: the caller's `useZodForm(ClassroomRequestSchema)`
 * already ran `validateAll()` before submit.
 */
export async function createClassroom(payload) {
  return api.postValidated('/api/v1/classrooms', payload, { schema: ClassroomResponseSchema });
}

/**
 * Updates an existing classroom. ADMIN only.
 * PUT /api/v1/classrooms/{uuid}
 */
export async function updateClassroom(uuid, payload) {
  return api.putValidated(`/api/v1/classrooms/${uuid}`, payload, { schema: ClassroomResponseSchema });
}

/**
 * Toggles the active status of a classroom. ADMIN only.
 * Flips isActive: active → inactive (hidden from catalog, no new reservations allowed)
 * or inactive/null → active. Child classrooms are not affected.
 * Reservation history is preserved (DFR NFR / LFTAIP).
 * PATCH /api/v1/classrooms/{uuid}/toggle-status
 *
 * @returns {Promise<object>} the updated classroom with its new isActive value
 */
export async function toggleClassroomStatus(uuid) {
  return api.patchValidated(`/api/v1/classrooms/${uuid}/toggle-status`, undefined, { schema: ClassroomResponseSchema });
}

/**
 * Deletes a classroom. ADMIN only.
 * DELETE /api/v1/classrooms/{uuid}
 */
export async function deleteClassroom(uuid) {
  return api.deleteValidated(`/api/v1/classrooms/${uuid}`);
}

/**
 * Atomically assigns a set of direct child classrooms to the given parent. ADMIN only.
 *
 * Sends the full desired set of child UUIDs; the backend diffs against the current state
 * and links/unlinks accordingly in a single transaction. An empty array removes all children.
 *
 * PUT /api/v1/classrooms/{parentUuid}/children
 *
 * @param {string}   parentUuid - UUID of the parent classroom.
 * @param {string[]} childUuids - Full desired set of child classroom UUIDs.
 */
export async function setClassroomChildren(parentUuid, childUuids) {
  return api.putValidated(`/api/v1/classrooms/${parentUuid}/children`, { childUuids });
}
