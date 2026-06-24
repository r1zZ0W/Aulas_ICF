/**
 * @fileoverview Zod schema for the GET /api/v1/users/stats response.
 * Matches UserStatsDTO: { total, active, inactive, admins } — all longs (serialized as numbers).
 */
import { z } from 'zod';

/**
 * Users stats schema.
 * {
 *    total: long,
 *    active: long,
 *    inactive: long,
 *    admins: long,
 * }
 * @returns {z.ZodObject}
 */
export const UserStatsSchema = z.object({
  total: z.number(),
  admins: z.number(),
});

export default UserStatsSchema;
