/**
 * @fileoverview Validation schema for recurring reservation group responses.
 */
import { z } from 'zod';
import { DayOfWeekEnum, ReservationGroupStatusEnum } from './enums.js';

/**
 * Zod schema for validating recurring reservation group details returned by the API.
 * Includes group identifiers, created timestamps, and associated academic semester names.
 */
export const ReservationGroupResponseSchema = z.object({
  uuid: z.string().uuid(),
  userUuid: z.string().uuid(),
  semesterName: z.string(),
  status: ReservationGroupStatusEnum,
  daysOfWeek: z.array(DayOfWeekEnum),
  createdAt: z.string()
});

export default ReservationGroupResponseSchema;
