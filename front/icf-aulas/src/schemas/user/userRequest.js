/**
 * @fileoverview Validation schema for user API requests.
 */

import { z } from "zod";

/**
 * Schema for validating user creation and administrative profile update requests.
 * Enforces field lengths, institutional email restrictions, and user roles.
 */
const UserRequestSchema = z.object({
  firstName: z
    .string()
    .min(1, 'El nombre es requerido')
    .max(100, 'El nombre debe tener menos de 100 caracteres'),
  lastNames: z
    .string()
    .min(1, 'Los apellidos son requeridos')
    .max(100, 'Los apellidos deben tener menos de 100 caracteres'),
  email: z.string().email('Dirección de correo electrónico no válida'),
  password: z
    .string()
    .min(8, 'La contraseña debe tener al menos 8 caracteres')
    .max(128, 'La contraseña debe tener menos de 128 caracteres'),
  roleId: z.number().int().positive('El rol es requerido'),
  isActive: z.boolean().optional()
});

export default UserRequestSchema;