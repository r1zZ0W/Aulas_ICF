import { createApiClient, HttpError } from './base.js';
import {
  ClassroomResponseSchema,
  ClassroomRequestSchema,
  ClassroomStatsSchema,
} from '../schemas/classroom.js';
import { PagedResultSchema } from '../schemas/pagedResult.js';
import { buildPageParams } from '../utils/queryUtils.js';

const api = createApiClient({
  baseURL: import.meta.env.VITE_API_URL ?? 'http://localhost:8080',
  headers: { Accept: 'application/json' },
});

function resolveErrorMessage(error, overrides = {}) {
  if (overrides[error.status]) return overrides[error.status];
  const serverMessage = error.data?.message;
  const defaults = {
    0:   'No se pudo conectar con el servidor. Verifica tu conexión.',
    400: serverMessage || 'Los datos enviados no son válidos.',
    401: 'No autorizado.',
    403: 'No tienes permisos para realizar esta acción.',
    404: 'El aula solicitada no existe.',
    405: 'Operación no permitida en este recurso.',
    409: serverMessage || 'Ya existe un aula con ese nombre.',
    500: 'Error interno del servidor. Intenta de nuevo más tarde.',
  };
  return defaults[error.status] || serverMessage || `Error inesperado (${error.status}).`;
}

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
  try {
    const qs = buildPageParams({ search, page, size, sort, direction });
    const { data } = await api.get(`/api/v1/classrooms${qs}`);
    return PagedResultSchema(ClassroomResponseSchema).parse(data.data);
  } catch (error) {
    if (error instanceof HttpError) throw new Error(resolveErrorMessage(error));
    throw error;
  }
}

/**
 * Retrieves a single classroom by its public UUID.
 * GET /api/v1/classrooms/{uuid}
 */
export async function getClassroom(uuid) {
  try {
    const { data } = await api.get(`/api/v1/classrooms/${uuid}`);
    return ClassroomResponseSchema.parse(data.data ?? data);
  } catch (error) {
    if (error instanceof HttpError) throw new Error(resolveErrorMessage(error));
    throw error;
  }
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
    const { data } = await api.get('/api/v1/classrooms/stats');
    return ClassroomStatsSchema.parse(data.data);
  } catch (error) {
    // 404 means the backend hasn't implemented the endpoint yet — return null
    // so the UI can show a graceful placeholder instead of an error toast.
    if (error instanceof HttpError && error.status === 404) return null;
    if (error instanceof HttpError) throw new Error(resolveErrorMessage(error));
    throw error;
  }
}

/**
 * Creates a new classroom. ADMIN only.
 * POST /api/v1/classrooms
 */
export async function createClassroom(payload) {
  try {
    const body = ClassroomRequestSchema.parse(payload);
    const { data } = await api.post('/api/v1/classrooms', body);
    return ClassroomResponseSchema.parse(data.data ?? data);
  } catch (error) {
    if (error instanceof HttpError) throw new Error(resolveErrorMessage(error));
    throw error;
  }
}

/**
 * Updates an existing classroom. ADMIN only.
 * PUT /api/v1/classrooms/{uuid}
 */
export async function updateClassroom(uuid, payload) {
  try {
    const body = ClassroomRequestSchema.parse(payload);
    const { data } = await api.put(`/api/v1/classrooms/${uuid}`, body);
    return ClassroomResponseSchema.parse(data.data ?? data);
  } catch (error) {
    if (error instanceof HttpError) throw new Error(resolveErrorMessage(error));
    throw error;
  }
}

/**
 * Deactivates a classroom (baja lógica). ADMIN only.
 * Marks the classroom as inactive and automatically unlinks its direct children
 * (orphan cleanup option A — see classrooms-status-endpoints.md).
 * Reservation history is preserved (DFR NFR / LFTAIP).
 * PATCH /api/v1/classrooms/{uuid}/deactivate
 */
export async function deactivateClassroom(uuid) {
  try {
    await api.patch(`/api/v1/classrooms/${uuid}/deactivate`);
  } catch (error) {
    if (error instanceof HttpError) throw new Error(resolveErrorMessage(error));
    throw error;
  }
}

/**
 * Reactivates a previously deactivated classroom. ADMIN only.
 * Child classrooms unlinked during the prior deactivation are NOT re-linked automatically;
 * the administrator must update each child's parent explicitly if needed.
 * PATCH /api/v1/classrooms/{uuid}/reactivate
 */
export async function reactivateClassroom(uuid) {
  try {
    await api.patch(`/api/v1/classrooms/${uuid}/reactivate`);
  } catch (error) {
    if (error instanceof HttpError) throw new Error(resolveErrorMessage(error));
    throw error;
  }
}
