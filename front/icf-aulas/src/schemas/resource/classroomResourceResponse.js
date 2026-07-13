/**
 * @fileoverview Validation schema for classroom resource allocation responses.
 */
import { z } from 'zod';

/**
 * Zod schema for validating and parsing classroom resource assignment responses returned by the API.
 * Maps classroom identifiers to resource types and active quantities.
 */
export const ClassroomResourceResponseSchema = z.object({
  classroomUuid: z.string().uuid(),
  resourceId: z.number().int(),
  resourceName: z.string(),
  quantity: z.number().int()
});

export default ClassroomResourceResponseSchema;
