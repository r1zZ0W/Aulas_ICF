/**
 * @fileoverview Validation schema for reservation reassignment requests.
 */
import { z } from 'zod';

/**
 * Zod schema for the reassignment request.
 * Requires at least one of `newClassroomUuid` or `newTimeSlotIds` to be provided.
 */
export const ReassignRequestSchema = z.object({
  newClassroomUuid: z.string().uuid().optional(),
  newTimeSlotIds: z.array(z.number().int()).min(1).optional(),
});

export default ReassignRequestSchema;
