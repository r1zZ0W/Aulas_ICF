/**
 * @fileoverview Validation schema for classroom API responses.
 */
import { z } from 'zod';

/**
 * Zod schema for validating and parsing classroom details returned from API responses.
 * Mirrors ClassroomResponseDTO.java.
 */
export const ClassroomResponseSchema = z.object({
  uuid: z.string(),
  name: z.string(),
  capacity: z.number().int(),
  type: z.string().optional(),
  description: z.string().nullable().optional(),
  linkedRoomUuid: z.string().nullable().optional(),
  isActive: z.boolean(),
});

export default ClassroomResponseSchema;
