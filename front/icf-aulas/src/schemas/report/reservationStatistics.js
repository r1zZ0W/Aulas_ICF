/**
 * @fileoverview Validation schema for reservation statistics.
 * Field names mirror the backend `ReservationStatisticsDTO` exactly — a mismatch
 * fails loudly at runtime via Zod, which is the contract-alignment check.
 */
import { z } from 'zod';

/** Statistics for a single classroom's total occupied hours. */
const ClassroomOccupancyItemSchema = z.object({
  name: z.string(),
  hours: z.number().nonnegative(),
});

/** Statistics for a user's total reservation instances count. */
const UserReservationsItemSchema = z.object({
  name: z.string(),
  reservations: z.number().int().nonnegative(),
});

/** Split of recurring vs one-time reservations (groups, not session-days). */
const RecurrenceSchema = z.object({
  recurring: z.number().int().nonnegative(),
  oneTime: z.number().int().nonnegative(),
});

/** A data point of the reservation-count trend for a specific label (day or month). */
const TrendPointSchema = z.object({
  label: z.string(),
  reservations: z.number().int().nonnegative(),
});

/**
 * Zod schema for validating the full reservation statistics payload.
 * Matches the backend ReservationStatisticsDTO.
 */
export const ReservationStatisticsSchema = z.object({
  totalReservations: z.number().int().nonnegative(),
  totalReservationsDeltaPct: z.number().nullable(),
  mostOccupiedClassroom: z.object({ name: z.string(), hours: z.number().nonnegative() }).nullable(),
  topUser: z.object({ name: z.string(), reservations: z.number().int().nonnegative() }).nullable(),
  recurrenceRatePct: z.number().min(0).max(100),

  mostOccupiedClassrooms: z.array(ClassroomOccupancyItemSchema),
  topUsers: z.array(UserReservationsItemSchema),
  recurrence: RecurrenceSchema,
  trend: z.array(TrendPointSchema),
});

export default ReservationStatisticsSchema;
