/**
 * @fileoverview Validation schemas for inventory resources and their physical allocation inside classrooms.
 * Handles validation rules for equipment types (projectors, ACs, etc.) and quantities assigned to specific rooms.
 */
import { z } from 'zod'

// ─── Resource (Equipment) ──────────────────────────────────────────────────

/**
 * Schema for validating individual inventory resources.
 * Ensures resource matches allowed hardware types and controls description lengths.
 */
export const ResourceSchema = z.object({
  id: z.number().int().positive().optional(),
  name: z.enum(['PROYECTOR', 'COMPUTADORA', 'AIRE_ACONDICIONADO', 'PANTALLA_TÁCTIL'], {
    errorMap: () => ({
      message:
        'El recurso debe ser un tipo válido (PROYECTOR, COMPUTADORA, AIRE_ACONDICIONADO, PANTALLA_TÁCTIL)'
    })
  }),
  description: z.string().max(255, 'La descripción debe tener menos de 255 caracteres').optional()
})

// ─── Classroom Resource (Allocation) ─────────────────────────────────────────

/**
 * Schema for requesting the allocation of an inventory resource to a specific classroom.
 * Validates classroom mappings, resource identifiers, and ensures quantity allocated is at least 1.
 */
export const ClassroomResourceRequestSchema = z.object({
  classroomId: z.number().int().positive('ID de aula no válido'),
  resourceId: z.number().int().positive('ID de recurso no válido'),
  quantity: z
    .number()
    .int()
    .min(1, 'La cantidad del recurso debe ser al menos 1')
    .default(1)
})

/**
 * Schema for validating and parsing classroom resource assignment responses returned by the API.
 * Maps classroom identifiers to resource types and active quantities.
 */
export const ClassroomResourceResponseSchema = z.object({
  classroomUuid: z.string().uuid(),
  resourceId: z.number().int(),
  resourceName: z.string(),
  quantity: z.number().int()
})

export default ResourceSchema
