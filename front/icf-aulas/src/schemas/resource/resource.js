/**
 * @fileoverview Validation schema for inventory resources (equipment).
 */
import { z } from 'zod';

/**
 * Zod schema for validating individual inventory resources.
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
});

export default ResourceSchema;
