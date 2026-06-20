/**
 * @fileoverview Validation schemas for academic semesters.
 * Mirrors SemesterRequestDTO.java and SemesterResponseDTO.java.
 *
 * Key design decisions:
 *  - `isActive` is a DERIVED field computed by the backend (today >= startDate && today <= endDate).
 *    It is NEVER sent in a create/update request; it lives only in the response schema.
 *  - Dates use `z.string().date()` which validates both the YYYY-MM-DD format AND that the
 *    calendar date actually exists (rejects "2026-02-31", "2026-09-35", etc.), preventing
 *    Spring's LocalDate parser from throwing on invalid dates that passed a regex-only check.
 *  - Cross-field validation (end > start) is a `.refine()` on the request schema only.
 *  - Mode-conditional rules ("no past dates" for create, relaxed for concluded semesters in edit)
 *    are enforced in useSemestersForm, not here, because they depend on runtime context.
 */
import { z } from 'zod'

// ── Request schema (create / update) ─────────────────────────────────────────

/**
 * Validates the payload sent to POST /api/v1/semesters and PUT /api/v1/semesters/{uuid}.
 * Mirrors SemesterRequestDTO.java.
 */
export const SemesterRequestSchema = z
  .object({
    name: z
      .string()
      .min(5, 'El nombre del semestre debe tener al menos 5 caracteres')
      .max(20, 'El nombre del semestre debe tener menos de 20 caracteres')
      .regex(
        /^\d{4}-[1-2]$/,
        'El formato del semestre debe ser YYYY-1 o YYYY-2 (ej: 2026-1)'
      ),

    startDate: z
      .string()
      .date('La fecha de inicio es inválida — verifica que el día exista en el calendario (YYYY-MM-DD)'),

    endDate: z
      .string()
      .date('La fecha de fin es inválida — verifica que el día exista en el calendario (YYYY-MM-DD)'),
  })
  .refine(
    (data) => data.startDate < data.endDate,
    {
      message: 'La fecha de fin debe ser posterior a la fecha de inicio',
      path: ['endDate'],
    }
  )

// ── Response schema ───────────────────────────────────────────────────────────

/**
 * Parses semester data received from API responses.
 * Mirrors SemesterResponseDTO.java.
 *
 * `isActive` is included here (read-only, derived by backend) but must NEVER be
 * sent back in a request payload.
 */
export const SemesterResponseSchema = z.object({
  uuid:      z.string(),
  name:      z.string(),
  startDate: z.string(),
  endDate:   z.string(),
  isActive:  z.boolean(),
  createdAt: z.string().optional(),
  updatedAt: z.string().optional(),
})

export default SemesterRequestSchema
