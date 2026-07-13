/**
 * @fileoverview Validation schema for classroom statistics.
 */
import { z } from 'zod';

/**
 * Zod schema for validating classroom usage and availability statistics.
 * Mirrors the planned ClassroomStatsDTO.
 */
export const ClassroomStatsSchema = z.object({
  total: z.number().int(),
  available: z.number().int(),
  notAvailable: z.number().int(),
});

export default ClassroomStatsSchema;
