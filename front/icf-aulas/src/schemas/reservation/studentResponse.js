/**
 * @fileoverview Validation schema for a single student in a reservation group's roster.
 */
import { z } from 'zod';

/**
 * Zod schema for validating a student entry returned by the admin-only
 * "view students" endpoint (GET /api/v1/reservations/groups/{groupUuid}/students).
 */
export const StudentResponseSchema = z.object({
  firstName: z.string(),
  lastName: z.string(),
  email: z.string(),
});

export default StudentResponseSchema;
