/**
 * @fileoverview Validation schemas for user API responses.
 */
import { z } from 'zod'

/** Institutional email domain restriction pattern ensuring only university domains (@icf.unam.mx) are registered. */
const ICF_EMAIL_REGEX = /^[^\s@]+@icf\.unam\.mx$/

/**
 * Schema for validating and parsing user information retrieved from administrative queries.
 * Contains user identifier, active status, creation dates, and role name metadata.
 */
export const UserResponseSchema = z.object({
  uuid: z.string().uuid(),
  firstName: z.string(),
  lastNames: z.string(),
  email: z.string().email(),
  roleName: z.string(),
  isActive: z.boolean(),
  createdAt: z.string().datetime()
})

export default UserResponseSchema;
