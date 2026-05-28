/**
 * @fileoverview Validation schemas for classrooms.
 * Defines structure, constraints, and data types for classroom creation,
 * updates, and API responses.
 */
import { z } from 'zod'

/**
 * Schema for validating classroom creation and update requests.
 * Ensures proper name length, capacity boundaries, and linked physical rooms.
 */
export const ClassroomRequestSchema = z.object({
  name: z
    .string()
    .min(3, 'El nombre debe tener al menos 3 caracteres')
    .max(50, 'El nombre debe tener menos de 50 caracteres'),
  capacity: z
    .number()
    .int()
    .min(1, 'La capacidad debe ser al menos 1')
    .max(500, 'La capacidad máxima admitida es de 500 personas'),
  /** Unique identifier of the adjacent or peer room physically connected to this classroom. */
  linkedRoomUuid: z.string().nullable().optional(),
  isActive: z.boolean().default(true)
})

/**
 * Schema for validating and structure mapping classroom data received from API responses.
 * Includes unique identifier, status, and linked room information.
 */
export const ClassroomResponseSchema = z.object({
  uuid: z.string(),
  name: z.string(),
  capacity: z.number().int(),
  linkedRoomUuid: z.string().nullable().optional(),
  isActive: z.boolean()
})

export default ClassroomRequestSchema
