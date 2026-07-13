/**
 * @fileoverview Validation schema for atomic booking requests.
 */
import { z } from 'zod';
import { DayOfWeekEnum } from './enums.js';

/**
 * Zod schema for the atomic booking request.
 * Creates a ReservationGroup and all its associated ReservInstance and ReservSlot records.
 */
export const BookingRequestSchema = z.object({
  classroomUuid: z.string(),
  attendeeCount: z.number().int().positive('El número de asistentes debe ser positivo').min(2),
  timeSlotIds: z.array(z.number().int()).min(1, 'Selecciona al menos un bloque de tiempo'),
  startDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Formato de fecha: YYYY-MM-DD'),
  title: z.preprocess(
    (v) => (typeof v === 'string' && v.trim() === '' ? undefined : v),
    z.string().trim().max(150, 'Máximo 150 caracteres').optional()
  ),
  daysOfWeek: z.array(DayOfWeekEnum).nullable().optional(),
});

export default BookingRequestSchema;
