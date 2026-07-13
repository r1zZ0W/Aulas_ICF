/**
 * @fileoverview Validation schema for individual reservation instance requests.
 */
import { z } from 'zod';
import { ReservInstanceStatusEnum } from './enums.js';

/**
 * Zod schema for requesting a single, concrete reservation instance on a specific date.
 * Validates the relationship between the reservation group, classroom, date, and status.
 */
export const ReservInstanceRequestSchema = z.object({
  groupUuid: z.string(),
  classroomUuid: z.string(),
  date: z
    .string()
    .regex(/^\d{4}-\d{2}-\d{2}$/, 'La fecha debe tener formato YYYY-MM-DD'),
  status: ReservInstanceStatusEnum.default('ACTIVE')
});

export default ReservInstanceRequestSchema;
