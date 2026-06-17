/**
 * @fileoverview Validation schemas for role catalog entries.
 */
import { z } from 'zod';

/**
 * Schema for validating role list items returned by GET /api/v1/roles.
 */
export const RoleResponseSchema = z.object({
  id: z.number().int().positive(),
  name: z.string(),
});

export default RoleResponseSchema;
