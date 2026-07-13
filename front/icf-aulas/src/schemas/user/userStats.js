/**
 * @fileoverview Zod schema for GET /api/v1/users/stats response.
 * Matches backend UserStatsDTO: { total, admins } or { total, active, inactive, admins } as numbers.
 */
import { z } from 'zod';

/**
 * Zod schema for validating user statistics payload.
 */
export const UserStatsSchema = z.object({
  total: z.number(),
  admins: z.number(),
});

export default UserStatsSchema;
