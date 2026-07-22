/**
 * @fileoverview Validation schemas for reservation-related enums.
 *
 * NOTE: this project's zod v4.4.3 silently ignores the old `errorMap` option (no error, no
 * warning — the message just never applies, falling back to Zod's generic English message
 * "Invalid option: expected one of ..."). Use the v4 `error` option instead.
 */
import { z } from 'zod';

/** Standard days of the week for scheduling recurring classroom reservations. */
export const DayOfWeekEnum = z.enum(
  ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'],
  { error: 'Día de la semana no válido' }
);

/** Operational status of a recurring reservation group. */
export const ReservationGroupStatusEnum = z.enum(['ACTIVE', 'CANCELLED'], {
  error: 'Estado de reserva no válido',
});

/** Individual status of a specific scheduled classroom reservation occurrence. */
export const ReservInstanceStatusEnum = z.enum(
  ['ACTIVE', 'CANCELLED_BY_USER', 'CANCELLED_BY_ADMIN'],
  { error: 'Estado de instancia no válido' }
);
