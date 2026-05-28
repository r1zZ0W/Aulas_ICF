/**
 * @fileoverview Validation schemas for system time slots.
 * Validates the time structures used to schedule bookings during the day.
 */
import { z } from 'zod'

/**
 * Schema for validating individual time blocks.
 * Enforces valid time slot ranges (1 to 24 blocks) and validates start and end times in HH:MM:SS format.
 */
export const TimeSlotSchema = z.object({
  id: z.number().int().min(1).max(24, 'ID de bloque no válido (1-24)'),
  startTime: z
    .string()
    .regex(
      /^([0-1]?\d|2[0-3]):[0-5]\d:[0-5]\d$/,
      'Formato de hora de inicio no válido (HH:MM:SS)'
    ),
  endTime: z
    .string()
    .regex(
      /^([0-1]?\d|2[0-3]):[0-5]\d:[0-5]\d$/,
      'Formato de hora de fin no válido (HH:MM:SS)'
    )
})

export default TimeSlotSchema
