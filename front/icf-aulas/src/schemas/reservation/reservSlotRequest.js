/**
 * @fileoverview Validation schema for reservation slot allocation requests.
 */
import { z } from 'zod';

/**
 * Zod schema for validating reservation slot requests.
 * Maps directly to core calendar booking mappings.
 */
export const ReservSlotRequestSchema = z.object({
  instanceId: z.number().int().positive('ID de instancia no válido'),
  timeSlotId: z.number().int().positive('ID de bloque de tiempo no válido'),
  classroomId: z.number().int().positive('ID de aula no válido'),
  userId: z.number().int().positive('ID de usuario no válido'),
  date: z
    .string()
    .regex(/^\d{4}-\d{2}-\d{2}$/, 'La fecha debe tener formato YYYY-MM-DD')
});

export default ReservSlotRequestSchema;
