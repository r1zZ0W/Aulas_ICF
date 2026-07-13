/**
 * @fileoverview Validation schema for recurring reservation group creation requests.
 */
import { z } from 'zod';
import { DayOfWeekEnum, ReservationGroupStatusEnum } from './enums.js';

/**
 * Zod schema for requesting the creation of a recurring reservation group.
 * Validates the associated user, academic semester, active status, and scheduled weekdays.
 */
export const ReservationGroupRequestSchema = z.object({
  userUuid: z.string().uuid('UUID de usuario no válido'),
  semesterId: z.number().int().positive('ID de semestre no válido'),
  status: ReservationGroupStatusEnum.default('ACTIVE'),
  daysOfWeek: z
    .array(DayOfWeekEnum)
    .min(1, 'Selecciona al menos un día de la semana')
});

export default ReservationGroupRequestSchema;
