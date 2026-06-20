/**
 * @fileoverview API client for the academic semesters module.
 * Mirrors src/api/classrooms.js in structure.
 *
 * Backend contract: back/aulas/docs/semesters-frontend-requests.md
 * All responses are wrapped in `ApiResponse`: { data: payload, error: false }
 *                                 or error:   { message: "...", error: true }
 */
import { z } from 'zod';
import { createApiClient, HttpError } from './base.js';
import { SemesterRequestSchema, SemesterResponseSchema } from '../schemas/semester.js';

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
    404: 'No hay un semestre activo actualmente.',
    409: serverMessage || 'Ya existe un semestre con ese nombre o rango de fechas.',
    500: 'Error interno del servidor. Intenta de nuevo más tarde.',
  };
  return defaults[error.status] || serverMessage || `Error inesperado (${error.status}).`;
}

/**
 * Returns the single active semester (whose date range contains today).
 * Returns `null` when no semester is active (404) — callers must handle this gracefully.
 * GET /api/v1/semesters/active
 */
export async function getActiveSemester() {
  try {
    const { data } = await api.get('/api/v1/semesters/active');
    return SemesterResponseSchema.parse(data.data);
  } catch (error) {
    // 404 means no semester is active today — return null instead of throwing,
    // so the UI can show "Sin semestre activo" gracefully.
    if (error instanceof HttpError && error.status === 404) return null;
    if (error instanceof HttpError) throw new Error(resolveErrorMessage(error));
    throw error;
  }
}

/**
 * Returns all semesters (past, active, and future).
 * Used to populate the semester dropdown in the split button.
 * GET /api/v1/semesters
 */
export async function getSemesters() {
  try {
    const { data } = await api.get('/api/v1/semesters');
    return z.array(SemesterResponseSchema).parse(data.data);
  } catch (error) {
    if (error instanceof HttpError) throw new Error(resolveErrorMessage(error));
    throw error;
  }
}

/**
 * Creates a new semester. ADMIN only.
 * POST /api/v1/semesters
 */
export async function createSemester(payload) {
  try {
    const body = SemesterRequestSchema.parse(payload);
    const { data } = await api.post('/api/v1/semesters', body);
    return SemesterResponseSchema.parse(data.data ?? data);
  } catch (error) {
    if (error instanceof HttpError) throw new Error(resolveErrorMessage(error));
    throw error;
  }
}

/**
 * Updates an existing semester. ADMIN only.
 * Sends all three editable fields: name, startDate, endDate.
 * PUT /api/v1/semesters/{uuid}
 */
export async function updateSemester(uuid, payload) {
  try {
    const body = SemesterRequestSchema.parse(payload);
    const { data } = await api.put(`/api/v1/semesters/${uuid}`, body);
    return SemesterResponseSchema.parse(data.data ?? data);
  } catch (error) {
    if (error instanceof HttpError) throw new Error(resolveErrorMessage(error));
    throw error;
  }
}
