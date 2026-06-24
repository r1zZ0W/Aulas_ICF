/**
 * @fileoverview Validation schemas for reservations, recurrent groups, instances, and slots.
 * Manages validation rules and structures for creating and displaying academic classroom reservations.
 *
 * Domain Structure:
 *  - ReservationGroup: A recurring schedule reservation spanning an entire academic semester.
 *  - ReservInstance: A concrete date-specific reservation occurrence belonging to a recurring group.
 *  - ReservSlot: A precise block of time allocated for a user in a specific classroom on a single date.
 */
import { z } from 'zod'
import { TimeSlotSchema } from './timeSlot.js'

// ─── Enums ────────────────────────────────────────────────────────────────────

/** Standard days of the week for scheduling recurring classroom reservations. */
export const DayOfWeekEnum = z.enum(
  ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'],
  { errorMap: () => ({ message: 'Día de la semana no válido' }) }
)

/** Operational status of a recurring reservation group. */
export const ReservationGroupStatusEnum = z.enum(['ACTIVE', 'CANCELLED'], {
  errorMap: () => ({ message: 'Estado de reserva no válido' })
})

/** Individual status of a specific scheduled classroom reservation occurrence. */
export const ReservInstanceStatusEnum = z.enum(
  ['ACTIVE', 'CANCELLED_BY_USER', 'CANCELLED_BY_ADMIN'],
  { errorMap: () => ({ message: 'Estado de instancia no válido' }) }
)

// ─── Reservation Group ────────────────────────────────────────────────────────

/**
 * Schema for requesting the creation of a recurring reservation group.
 * Validates the associated user, academic semester, active status, and scheduled weekdays.
 */
export const ReservationGroupRequestSchema = z.object({
  userUuid: z.string().uuid('UUID de usuario no válido'),
  semesterId: z.number().int().positive('ID de semestre no válido'),
  status: ReservationGroupStatusEnum.default('ACTIVE'),
  daysOfWeek: z
    .array(DayOfWeekEnum)
    .min(1, 'Selecciona al menos un día de la semana')
})

/**
 * Schema for validating and parsing recurring reservation group details returned by the API.
 * Includes group identifiers, created timestamps, and associated academic semester names.
 */
export const ReservationGroupResponseSchema = z.object({
  uuid: z.string().uuid(),
  userUuid: z.string().uuid(),
  semesterName: z.string(),
  status: ReservationGroupStatusEnum,
  daysOfWeek: z.array(DayOfWeekEnum),
  createdAt: z.string()
})

// ─── Reservation Instance ─────────────────────────────────────────────────────

/**
 * Schema for requesting a single, concrete reservation instance on a specific date.
 * Validates the relationship between the reservation group, classroom, date, and status.
 */
export const ReservInstanceRequestSchema = z.object({
  groupUuid: z.string(),
  classroomUuid: z.string(),
  date: z
    .string()
    .regex(/^\d{4}-\d{2}-\d{2}$/, 'La fecha debe tener formato YYYY-MM-DD'),
  status: ReservInstanceStatusEnum.default('ACTIVE')
})

/**
 * Schema for validating and mapping reservation instance information retrieved from API responses.
 * Includes the enriched `classroomName`, `motivo`, `numAsistentes`, and `timeSlots` fields
 * returned by the backend since v3.0 of the DTO.
 */
export const ReservInstanceResponseSchema = z.object({
  uuid: z.string(),
  groupUuid: z.string(),
  classroomUuid: z.string(),
  classroomName: z.string().optional().default(''),
  date: z.string(),
  status: ReservInstanceStatusEnum,
  attendeeCount: z.number().int().min(0),
  timeSlots: z.array(TimeSlotSchema).default([]),
  createdAt: z.string().optional(),
})

/**
 * Schema for the atomic booking request.
 * A single call creates the ReservationGroup and all its ReservInstance + ReservSlot rows.
 */
export const BookingRequestSchema = z.object({
  classroomUuid: z.string(),
  attendeeCount: z.number().int().positive('El número de asistentes debe ser positivo').min(2),
  timeSlotIds: z.array(z.number().int()).min(1, 'Selecciona al menos un bloque de tiempo'),
  startDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Formato de fecha: YYYY-MM-DD'),
  repeatUntil: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).nullable().optional(),
  daysOfWeek: z.array(DayOfWeekEnum).nullable().optional(),
})

/**
 * Schema for the reassignment request.
 * At least one of `newClassroomUuid` or `newTimeSlotIds` must be provided.
 */
export const ReassignRequestSchema = z.object({
  newClassroomUuid: z.string().uuid().optional(),
  newTimeSlotIds: z.array(z.number().int()).min(1).optional(),
})

// ─── Reservation Slot ─────────────────────────────────────────────────────────

/**
 * Schema for validating reservation slot requests.
 * Ensures valid numerical identifiers for specific room, user, time block, and date mapping.
 * @note Uses simple integer IDs rather than UUIDs to map directly to core calendar structures.
 */
export const ReservSlotRequestSchema = z.object({
  instanceId: z.number().int().positive('ID de instancia no válido'),
  timeSlotId: z.number().int().positive('ID de bloque de tiempo no válido'),
  classroomId: z.number().int().positive('ID de aula no válido'),
  userId: z.number().int().positive('ID de usuario no válido'),
  date: z
    .string()
    .regex(/^\d{4}-\d{2}-\d{2}$/, 'La fecha debe tener formato YYYY-MM-DD')
})

/**
 * Schema for validating and structure mapping reservation slot details in API responses.
 * Includes precise start/end time validation, room mapping, and user associations.
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
})

export default ReservationGroupRequestSchema
