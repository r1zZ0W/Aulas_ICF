/**
 * @fileoverview API client for the academic semesters module.
 * Mirrors src/api/classrooms.js in structure.
 *
 * Backend contract: back/aulas/docs/semesters-frontend-requests.md
 * All responses are wrapped in `ApiResponse`: { data: payload, error: false, code? }
 */
import { z } from 'zod';
import { createApiClient } from './base.js';
import { SemesterResponseSchema } from '../schemas/semester.js';
import { resolveApiError } from '../errors/resolveApiError.js';
import { ApiError } from '../errors/ApiError.js';

const api = createApiClient();

/**
 * Returns the single active semester (whose date range contains today).
 * Returns `null` when no semester is active (404) — callers must handle this gracefully.
 * GET /api/v1/semesters/active
 */
export async function getActiveSemester() {
  try {
    return await api.getValidated('/api/v1/semesters/active', { schema: SemesterResponseSchema });
  } catch (error) {
    // 404 means no semester is active today — return null instead of throwing,
    // so the UI can show "Sin semestre activo" gracefully.
    if (error instanceof ApiError && error.status === 404) return null;
    throw resolveApiError(error);
  }
}

/**
 * Returns all semesters (past, active, and future).
 * Used to populate the semester dropdown in the split button.
 * GET /api/v1/semesters
 */
export async function getSemesters() {
  return api.getValidated('/api/v1/semesters', { schema: z.array(SemesterResponseSchema) });
}

/**
 * Creates a new semester. ADMIN only.
 * POST /api/v1/semesters
 *
 * `payload` is not re-validated here: the caller's `useZodForm(SemesterRequestSchema)`
 * already ran `validateAll()` before submit.
 */
export async function createSemester(payload) {
  return api.postValidated('/api/v1/semesters', payload, { schema: SemesterResponseSchema });
}

/**
 * Updates an existing semester. ADMIN only.
 * Sends all three editable fields: name, startDate, endDate.
 * PUT /api/v1/semesters/{uuid}
 */
export async function updateSemester(uuid, payload) {
  return api.putValidated(`/api/v1/semesters/${uuid}`, payload, { schema: SemesterResponseSchema });
}
