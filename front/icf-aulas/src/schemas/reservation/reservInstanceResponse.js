/**
 * @fileoverview Validation schema for individual reservation instance responses.
 */
import { z } from 'zod';
import { ReservInstanceStatusEnum } from './enums.js';
import { TimeSlotSchema } from '../timeSlot.js';

/**
 * Zod schema for validating and mapping reservation instance information retrieved from API responses.
 * Includes the enriched classroomName, user details, date, status, attendeeCount, and time slots.
 */
export const ReservInstanceResponseSchema = z.object({
  uuid: z.string(),
  groupUuid: z.string(),
  userUuid: z.string(),
  userFullName: z.string(),
  userUsername: z.string(),
  classroomUuid: z.string(),
  classroomName: z.string().optional().default(''),
  date: z.string(),
  status: ReservInstanceStatusEnum,
  attendeeCount: z.number().int().min(0),
  timeSlots: z.array(TimeSlotSchema).default([]),
  createdAt: z.string().optional(),
  /**
   * Display hint set by the backend when an admin has reassigned this instance.
   */
  reassigned: z.boolean().optional().default(false),
  /** Free-form class/event label. */
  title: z.string().nullable().optional(),
});

export default ReservInstanceResponseSchema;
