import { z } from 'zod';
import { createApiClient } from './base.js';
import {
  ResourceResponseSchema,
  ResourceStatsSchema,
  ResourceCatalogItemSchema,
  ClassroomResourceResponseSchema,
} from '../schemas/resource.js';
import { PagedResultSchema } from '../schemas/pagedResult.js';
import { buildPageParams } from '../utils/queryUtils.js';

const api = createApiClient();

// ── Global resource catalog (admin CRUD) ────────────────────────────────────────

/**
 * Fetches a paginated, optionally-filtered page of the global equipment catalog. ADMIN reads
 * are unrestricted (any authenticated user may GET); writes are ADMIN only (enforced server-side).
 *
 * @param {object} [params={}]
 * @param {string}       [params.search]    - Free-text filter over name/description.
 * @param {number}       [params.page]      - Zero-based page index.
 * @param {number}       [params.size]      - Page size.
 * @param {string}       [params.sort]      - Sort field. Allowed: name, quantity.
 * @param {'asc'|'desc'} [params.direction] - Sort direction.
 * @returns {Promise<{items: object[], totalElements: number, totalPages: number}>}
 */
export async function getResources({ search, page, size, sort, direction } = {}) {
  const qs = buildPageParams({ search, page, size, sort, direction });
  return api.getValidated(`/api/v1/resources${qs}`, { schema: PagedResultSchema(ResourceResponseSchema) });
}

/**
 * Fetches aggregated resource statistics (total types, total units) for the admin dashboard.
 * GET /api/v1/resources/stats
 *
 * @returns {Promise<{ totalTypes: number, totalUnits: number }>}
 */
export async function getResourceStats() {
  return api.getValidated('/api/v1/resources/stats', { schema: ResourceStatsSchema });
}

/**
 * Fetches a single equipment resource by its public UUID.
 * GET /api/v1/resources/{uuid}
 */
export async function getResource(uuid) {
  return api.getValidated(`/api/v1/resources/${uuid}`, { schema: ResourceResponseSchema });
}

/**
 * Creates a new equipment resource in the global catalog. ADMIN only.
 * POST /api/v1/resources
 *
 * `payload` is not re-validated here: the caller's `useZodForm(ResourceRequestSchema)`
 * already ran `validateAll()` before submit.
 */
export async function createResource(payload) {
  return api.postValidated('/api/v1/resources', payload, { schema: ResourceResponseSchema });
}

/**
 * Updates an existing equipment resource in the global catalog. ADMIN only.
 * PUT /api/v1/resources/{uuid}
 */
export async function updateResource(uuid, payload) {
  return api.putValidated(`/api/v1/resources/${uuid}`, payload, { schema: ResourceResponseSchema });
}

/**
 * Deletes an equipment resource from the global catalog. ADMIN only.
 *
 * Any classroom allocations referencing this resource are removed by the backend's
 * `ON DELETE CASCADE` — no orphaned `classroom_resources` rows are left behind.
 *
 * DELETE /api/v1/resources/{uuid}
 */
export async function deleteResource(uuid) {
  return api.deleteValidated(`/api/v1/resources/${uuid}`);
}

// ── Classroom ⇄ equipment allocation ────────────────────────────────────────────

/** Page size used while walking the equipment catalog — see getResourceCatalog(). */
const CATALOG_PAGE_SIZE = 100;

/**
 * Retrieves the FULL equipment resource catalog (every page, not just the first).
 *
 * GET /api/v1/resources does not accept a `search` param — `ResourceController.findAll` only
 * takes paging/sort criteria — so the "Agregar recurso" picker has no server-side filter to lean
 * on and needs the complete catalog to filter client-side (already-assigned exclusion). Rather
 * than hardcode a single large `size` and silently truncate the list once the catalog outgrows
 * it, this walks every page **sequentially** (not `Promise.all`, to avoid flooding the backend on
 * a congested connection) and concatenates the results.
 *
 * Runs as the queryFn of a `useQuery` (see useClassroomResources.js): if any page's request
 * fails, the whole promise rejects and React Query's default retry policy re-runs the entire
 * fetch, instead of silently returning a half-loaded catalog. Cost is O(pages), linear — accepted
 * as the equipment catalog is expected to stay small.
 *
 * @returns {Promise<Array<{uuid:string, name:string, description?:string|null, quantity?:number}>>}
 */
export async function getResourceCatalog() {
  const items = [];
  let page = 0;
  let totalPages;
  do {
    const parsed = await api.getValidated(
      `/api/v1/resources?page=${page}&size=${CATALOG_PAGE_SIZE}&sort=name&direction=asc`,
      { schema: PagedResultSchema(ResourceCatalogItemSchema) },
    );
    items.push(...parsed.items);
    totalPages = parsed.totalPages;
    page += 1;
  } while (page < totalPages);
  return items;
}

/**
 * Retrieves the equipment allocations currently assigned to a classroom.
 * GET /api/v1/classrooms/{classroomUuid}/resources
 */
export async function getClassroomResources(classroomUuid) {
  return api.getValidated(`/api/v1/classrooms/${classroomUuid}/resources`, {
    schema: z.array(ClassroomResourceResponseSchema),
  });
}

/**
 * Assigns or updates the quantity of an equipment resource for a classroom (upsert). ADMIN only.
 * POST /api/v1/classrooms/{classroomUuid}/resources
 *
 * Sends only `{ resourceUuid, quantity }` — the backend derives the classroom from the path UUID.
 * `payload` is not re-validated here: `ClassroomResourcesModal` already runs
 * `ClassroomResourceMutationSchema.safeParse` before calling this.
 *
 * @param {string} classroomUuid
 * @param {{ resourceUuid: string, quantity: number }} payload
 */
export async function assignClassroomResource(classroomUuid, payload) {
  return api.postValidated(`/api/v1/classrooms/${classroomUuid}/resources`, payload, {
    schema: ClassroomResourceResponseSchema,
  });
}

/**
 * Removes an equipment allocation from a classroom. ADMIN only.
 * DELETE /api/v1/classrooms/{classroomUuid}/resources/{resourceUuid}
 */
export async function removeClassroomResource(classroomUuid, resourceUuid) {
  return api.deleteValidated(`/api/v1/classrooms/${classroomUuid}/resources/${resourceUuid}`);
}
