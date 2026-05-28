/**
 * @fileoverview Validation schemas for user roles within the system.
 * Manages structures and constraints for system roles like Administrator and Teacher.
 */
import { z } from 'zod'

/**
 * Schema for validating and structure mapping user role details.
 * Restricts roles to allowed types, enforces description limits, and tracks creation dates.
 */
export const RoleSchema = z.object({
  name: z.enum(['MAESTRO', 'ADMIN'], {
    errorMap: () => ({ message: 'El rol debe ser MAESTRO o ADMIN' })
  }),
  description: z.string().max(255, 'La descripción debe tener menos de 255 caracteres').optional(),
  createdAt: z.string().datetime().optional()
})

export default RoleSchema
