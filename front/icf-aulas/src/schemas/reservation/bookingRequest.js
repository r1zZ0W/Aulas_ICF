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
  // Mirrors the backend's @Positive on BookingRequestDTO.attendeeCount (min 1, not 2) —
  // see docs/plan note on schema alignment. The UI itself offers 1..capacity.
  attendeeCount: z.number().int().positive('El número de asistentes debe ser positivo'),
  timeSlotIds: z.array(z.number().int()).min(1, 'Selecciona al menos un bloque de tiempo'),
  startDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Formato de fecha: YYYY-MM-DD'),
  title: z.preprocess(
    (v) => (typeof v === 'string' && v.trim() === '' ? undefined : v),
    z.string().trim().max(150, 'Máximo 150 caracteres').optional()
  ),
  daysOfWeek: z.array(DayOfWeekEnum).nullable().optional(),
  /**
   * Admin-only: book on behalf of a different user (teacher or admin) instead of the
   * caller. Only ever populated when the "Reservar para otro usuario" toggle is on
   * (see useReservaModal.js); the backend independently rejects it for non-admins.
   */
  userUuid: z.string().uuid().optional(),
});

export default BookingRequestSchema;
