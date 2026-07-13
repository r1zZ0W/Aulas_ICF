/**
 * @fileoverview Validation schemas for reservation-related enums.
 */
import { z } from 'zod';

/** Standard days of the week for scheduling recurring classroom reservations. */
export const DayOfWeekEnum = z.enum(
  ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'],
  { errorMap: () => ({ message: 'Día de la semana no válido' }) }
);

/** Operational status of a recurring reservation group. */
export const ReservationGroupStatusEnum = z.enum(['ACTIVE', 'CANCELLED'], {
  errorMap: () => ({ message: 'Estado de reserva no válido' })
});

/** Individual status of a specific scheduled classroom reservation occurrence. */
export const ReservInstanceStatusEnum = z.enum(
  ['ACTIVE', 'CANCELLED_BY_USER', 'CANCELLED_BY_ADMIN'],
  { errorMap: () => ({ message: 'Estado de instancia no válido' }) }
);
