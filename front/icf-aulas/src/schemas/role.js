/**
 * @fileoverview Validation schemas for role catalog entries.
 */
import { z } from 'zod';

/**
 * Zod schema for validating role details returned by GET /api/v1/roles.
 */
export const RoleResponseSchema = z.object({
  id: z.number().int().positive(),
  name: z.string(),
});

export default RoleResponseSchema;
