/**
 * @fileoverview Validation schema for classroom create / update requests.
 */
import { z } from 'zod';
import { CLASSROOM_TYPES_MAP } from './classroomTypes.js';

/**
 * Zod schema for validating classroom write payloads (create/update).
 * Mirrors ClassroomRequestDTO.java field-by-field.
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
    Object.keys(CLASSROOM_TYPES_MAP),
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
});

export default ClassroomRequestSchema;
