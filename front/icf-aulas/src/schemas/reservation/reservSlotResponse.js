/**
 * @fileoverview Validation schema for reservation slot allocation responses.
 */
import { z } from 'zod';

/**
 * Zod schema for validating and mapping reservation slot details in API responses.
 * Includes start/end time validation, classroom mapping, and user associations.
 */
export const ReservSlotResponseSchema = z.object({
  instanceUuid: z.string(),
  timeSlotId: z.number().int(),
  startTime: z
    .string()
    .regex(/^([0-1]?\d|2[0-3]):[0-5]\d:[0-5]\d$/, 'Formato de hora no válido (HH:MM:SS)'),
  endTime: z
    .string()
    .regex(/^([0-1]?\d|2[0-3]):[0-5]\d:[0-5]\d$/, 'Formato de hora no válido (HH:MM:SS)'),
  classroomId: z.number().int(),
  userId: z.number().int(),
  date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/)
});

export default ReservSlotResponseSchema;
