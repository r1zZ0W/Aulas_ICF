/**
 * @fileoverview Validation schemas and helpers for classrooms.
 * Mirrors ClassroomRequestDTO.java and ClassroomResponseDTO.java.
 * CLASSROOM_TYPES_MAP is the single source of truth for all type-related logic.
 */
import { z } from 'zod'

// ── Classroom-type catalogue ──────────────────────────────────────────────────

/** Maps ClassroomType enum values (backend) to Spanish display labels. */
export const CLASSROOM_TYPES_MAP = {
  AULA: 'Aula',
  AUDITORIO: 'Auditorio',
  LABORATORIO: 'Laboratorio',
  SALA_SEMINARIOS: 'Sala de seminarios',
}

/** Array of { value, label } pairs ready for <Select> components. */
export const CLASSROOM_TYPES = Object.entries(CLASSROOM_TYPES_MAP).map(
  ([value, label]) => ({ value, label })
)

/** Returns the Spanish display label for a ClassroomType key. Falls back to the key itself. */
export const typeLabel = (type) => CLASSROOM_TYPES_MAP[type] ?? type ?? '—';

// ── Request schema ────────────────────────────────────────────────────────────

/**
 * Client-side validation for classroom create / update payloads.
 * Mirrors ClassroomRequestDTO.java field-by-field.
 *
 * Key normalisation rules:
 *  - `type` is validated as a strict enum against CLASSROOM_TYPES_MAP.
 *  - `description` is trimmed and empty strings are transformed to null so the
 *    backend never stores a blank string (avoids blank-vs-null inconsistency).
 */
export const ClassroomRequestSchema = z.object({
  name: z
    .string()
    .min(3, 'El nombre debe tener al menos 3 caracteres')
    .max(100, 'El nombre debe tener menos de 100 caracteres'),

  capacity: z
    .number()
    .int()
    .min(1, 'La capacidad debe ser al menos 1')
    .max(500, 'La capacidad máxima admitida es de 500 personas'),

  type: z.enum(
    /** @type {[string, ...string[]]} */(Object.keys(CLASSROOM_TYPES_MAP)),
    { errorMap: () => ({ message: 'Selecciona un tipo de aula válido' }) }
  ),

  description: z
    .string()
    .trim()
    .max(500, 'La descripción debe tener menos de 500 caracteres')
    .transform((v) => (v === '' ? null : v))
    .nullable()
    .optional(),

  linkedRoomUuid: z.string().nullable().optional(),

  isActive: z.boolean().default(true),
})

// ── Response schema ───────────────────────────────────────────────────────────

/**
 * Parses classroom data received from API responses.
 * Mirrors ClassroomResponseDTO.java — type and description are included.
 */
export const ClassroomResponseSchema = z.object({
  uuid: z.string(),
  name: z.string(),
  capacity: z.number().int(),
  type: z.string().optional(),
  description: z.string().nullable().optional(),
  linkedRoomUuid: z.string().nullable().optional(),
  isActive: z.boolean(),
})

// ── Stats schema (pending backend endpoint) ───────────────────────────────────

/**
 * Parses the response from GET /api/v1/classrooms/stats.
 * Fields mirror the planned ClassroomStatsDTO — see back/aulas/docs/classrooms-frontend-requests.md.
 */
export const ClassroomStatsSchema = z.object({
  total: z.number().int(),
  available: z.number().int(),
  notAvailable: z.number().int(),
})

export default ClassroomRequestSchema
