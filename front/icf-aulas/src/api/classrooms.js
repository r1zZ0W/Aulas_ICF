import { createApiClient, HttpError } from './base.js';
import { ClassroomResponseSchema, ClassroomRequestSchema } from '../schemas/classroom.js';
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
    0: 'No se pudo conectar con el servidor. Verifica tu conexión.',
    400: serverMessage || 'Los datos enviados no son válidos.',
    401: 'No autorizado.',
    403: 'No tienes permisos para realizar esta acción.',
    404: 'El aula solicitada no existe.',
    500: 'Error interno del servidor. Intenta de nuevo más tarde.',
  };
  return defaults[error.status] || serverMessage || `Error inesperado (${error.status}).`;
}

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

export async function getClassroom(uuid) {
  try {
    const { data } = await api.get(`/api/v1/classrooms/${uuid}`);
    return ClassroomResponseSchema.parse(data.data ?? data);
  } catch (error) {
    if (error instanceof HttpError) throw new Error(resolveErrorMessage(error));
    throw error;
  }
}

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

