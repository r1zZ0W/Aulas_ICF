/**
 * @fileoverview Validation schema for user API responses.
 */
import { z } from 'zod';

/**
 * Schema for validating and parsing user information retrieved from administrative queries.
 * Matches the backend UserResponseDTO exactly.
 */
export const UserResponseSchema = z.object({
  uuid: z.string(),
  matricula: z.string().nullable().optional(),
  firstName: z.string(),
  lastNames: z.string(),
  username: z.string(),
  email: z.string(),
  extension: z.string().nullable().optional(),
  roleName: z.string(),
  createdAt: z.string(),
});

export default UserResponseSchema;
