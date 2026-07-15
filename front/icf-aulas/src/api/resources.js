import { z } from 'zod';
import { createApiClient, HttpError } from './base.js';
import {
  ResourceCatalogItemSchema,
  ClassroomResourceResponseSchema,
  ClassroomResourceMutationSchema,
} from '../schemas/resource.js';
import { PagedResultSchema } from '../schemas/pagedResult.js';

const api = createApiClient({
  baseURL: import.meta.env.VITE_API_URL ?? 'http://localhost:8080',
  headers: { Accept: 'application/json' },
});

function resolveErrorMessage(error, overrides = {}) {
  if (overrides[error.status]) return overrides[error.status];
  const serverMessage = error.data?.message;
  const defaults = {
    0: 'No se pudo conectar con el servidor. Verifica tu conexión.',
    400: 'Los datos enviados no son válidos.',
    401: 'No autorizado.',
    403: 'No tienes permisos para realizar esta acción.',
    404: 'El recurso solicitado no existe.',
    500: 'Error interno del servidor. Intenta de nuevo más tarde.',
  };
  return defaults[error.status] || serverMessage || `Error inesperado (${error.status}).`;
}

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
 * @returns {Promise<Array<{id:number, name:string, description?:string|null}>>}
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
 * Sends only `{ resourceId, quantity }` — the backend derives the classroom from the path UUID
 * and ignores `classroomId` in the request body (see ClassroomResourceService.save), so there is
 * no numeric classroom id to send from the frontend, which only ever holds the UUID.
 *
 * @param {string} classroomUuid
 * @param {{ resourceId: number, quantity: number }} payload
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
 * DELETE /api/v1/classrooms/{classroomUuid}/resources/{resourceId}
 */
export async function removeClassroomResource(classroomUuid, resourceId) {
  try {
    await api.delete(`/api/v1/classrooms/${classroomUuid}/resources/${resourceId}`);
  } catch (error) {
    if (error instanceof HttpError) throw new Error(resolveErrorMessage(error));
    throw error;
  }
}
