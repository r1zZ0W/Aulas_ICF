import { z } from 'zod';
import { createApiClient, HttpError } from './base.js';
import {
  ResourceRequestSchema,
  ResourceResponseSchema,
  ResourceStatsSchema,
  ResourceCatalogItemSchema,
  ClassroomResourceResponseSchema,
  ClassroomResourceMutationSchema,
} from '../schemas/resource.js';
import { PagedResultSchema } from '../schemas/pagedResult.js';
import { buildPageParams } from '../utils/queryUtils.js';

const api = createApiClient();

function resolveErrorMessage(error, overrides = {}) {
  if (overrides[error.status]) return overrides[error.status];
  const serverMessage = error.data?.message;
  const defaults = {
    0: 'No se pudo conectar con el servidor. Verifica tu conexión.',
    400: 'Los datos enviados no son válidos.',
    401: 'No autorizado.',
    403: 'No tienes permisos para realizar esta acción.',
    404: 'El recurso solicitado no existe.',
    409: 'Ya existe un recurso con ese nombre.',
    500: 'Error interno del servidor. Intenta de nuevo más tarde.',
  };
  return defaults[error.status] || serverMessage || `Error inesperado (${error.status}).`;
}

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
  try {
    const qs = buildPageParams({ search, page, size, sort, direction });
    const { data } = await api.get(`/api/v1/resources${qs}`);
    return PagedResultSchema(ResourceResponseSchema).parse(data.data);
  } catch (error) {
    if (error instanceof HttpError) throw new Error(resolveErrorMessage(error));
    throw error;
  }
}

/**
 * Fetches aggregated resource statistics (total types, total units) for the admin dashboard.
 * GET /api/v1/resources/stats
 *
 * @returns {Promise<{ totalTypes: number, totalUnits: number }>}
 */
export async function getResourceStats() {
  try {
    const { data } = await api.get('/api/v1/resources/stats');
    return ResourceStatsSchema.parse(data.data);
  } catch (error) {
    if (error instanceof HttpError) throw new Error(resolveErrorMessage(error));
    throw error;
  }
}

/**
 * Fetches a single equipment resource by its public UUID.
 * GET /api/v1/resources/{uuid}
 */
export async function getResource(uuid) {
  try {
    const { data } = await api.get(`/api/v1/resources/${uuid}`);
    return ResourceResponseSchema.parse(data.data ?? data);
  } catch (error) {
    if (error instanceof HttpError) throw new Error(resolveErrorMessage(error));
    throw error;
  }
}

/**
 * Creates a new equipment resource in the global catalog. ADMIN only.
 * POST /api/v1/resources
 */
export async function createResource(payload) {
  try {
    const body = ResourceRequestSchema.parse(payload);
    const { data } = await api.post('/api/v1/resources', body);
    return ResourceResponseSchema.parse(data.data ?? data);
  } catch (error) {
    if (error instanceof HttpError) {
      throw new Error(resolveErrorMessage(error, {
        409: 'Ya existe un recurso con ese nombre.',
      }));
    }
    throw error;
  }
}

/**
 * Updates an existing equipment resource in the global catalog. ADMIN only.
 * PUT /api/v1/resources/{uuid}
 */
export async function updateResource(uuid, payload) {
  try {
    const body = ResourceRequestSchema.parse(payload);
    const { data } = await api.put(`/api/v1/resources/${uuid}`, body);
    return ResourceResponseSchema.parse(data.data ?? data);
  } catch (error) {
    if (error instanceof HttpError) {
      throw new Error(resolveErrorMessage(error, {
        409: 'Ya existe un recurso con ese nombre.',
      }));
    }
    throw error;
  }
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
  try {
    await api.delete(`/api/v1/resources/${uuid}`);
  } catch (error) {
    if (error instanceof HttpError) throw new Error(resolveErrorMessage(error));
    throw error;
  }
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
  try {
    const items = [];
    let page = 0;
    let totalPages = 1;
    do {
      const { data } = await api.get(
        `/api/v1/resources?page=${page}&size=${CATALOG_PAGE_SIZE}&sort=name&direction=asc`
      );
      const parsed = PagedResultSchema(ResourceCatalogItemSchema).parse(data.data ?? data);
      items.push(...parsed.items);
      totalPages = parsed.totalPages;
      page += 1;
    } while (page < totalPages);
    return items;
  } catch (error) {
    if (error instanceof HttpError) throw new Error(resolveErrorMessage(error));
    throw error;
  }
}

/**
 * Retrieves the equipment allocations currently assigned to a classroom.
 * GET /api/v1/classrooms/{classroomUuid}/resources
 */
export async function getClassroomResources(classroomUuid) {
  try {
    const { data } = await api.get(`/api/v1/classrooms/${classroomUuid}/resources`);
    return z.array(ClassroomResourceResponseSchema).parse(data.data ?? data);
  } catch (error) {
    if (error instanceof HttpError) throw new Error(resolveErrorMessage(error));
    throw error;
  }
}

/**
 * Assigns or updates the quantity of an equipment resource for a classroom (upsert). ADMIN only.
 * POST /api/v1/classrooms/{classroomUuid}/resources
 *
 * Sends only `{ resourceUuid, quantity }` — the backend derives the classroom from the path UUID.
 *
 * @param {string} classroomUuid
 * @param {{ resourceUuid: string, quantity: number }} payload
 */
export async function assignClassroomResource(classroomUuid, payload) {
  try {
    const body = ClassroomResourceMutationSchema.parse(payload);
    const { data } = await api.post(`/api/v1/classrooms/${classroomUuid}/resources`, body);
    return ClassroomResourceResponseSchema.parse(data.data ?? data);
  } catch (error) {
    if (error instanceof HttpError) throw new Error(resolveErrorMessage(error));
    throw error;
  }
}

/**
 * Removes an equipment allocation from a classroom. ADMIN only.
 * DELETE /api/v1/classrooms/{classroomUuid}/resources/{resourceUuid}
 */
export async function removeClassroomResource(classroomUuid, resourceUuid) {
  try {
    await api.delete(`/api/v1/classrooms/${classroomUuid}/resources/${resourceUuid}`);
  } catch (error) {
    if (error instanceof HttpError) throw new Error(resolveErrorMessage(error));
    throw error;
  }
}
