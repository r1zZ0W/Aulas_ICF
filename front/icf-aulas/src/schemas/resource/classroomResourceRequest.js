/**
 * @fileoverview Validation schema for allocating resources to specific classrooms.
 */
import { z } from 'zod';

/**
 * Zod schema for requesting the allocation of an inventory resource to a specific classroom.
 * Validates classroom mappings, resource identifiers, and quantity.
 */
export const ClassroomResourceRequestSchema = z.object({
  classroomId: z.number().int().positive('ID de aula no válido'),
  resourceId: z.number().int().positive('ID de recurso no válido'),
  quantity: z
    .number()
    .int()
    .min(1, 'La cantidad del recurso debe ser al menos 1')
    .default(1)
});

export default ClassroomResourceRequestSchema;
