/**
 * @fileoverview Validation schema for users editing their own profiles.
 */
import { z } from 'zod';
import { USERNAME_REGEX } from './constants.js';

/**
 * Zod schema for current-user profile edits.
 * Matches backend PUT /api/v1/users/me payload requirements.
 */
export const UserSelfEditSchema = z.object({
  firstName: z
    .string()
    .min(1, 'El nombre es requerido')
    .max(100, 'El nombre debe tener menos de 100 caracteres'),
  lastNames: z
    .string()
    .min(1, 'Los apellidos son requeridos')
    .max(100, 'Los apellidos deben tener menos de 100 caracteres'),
  username: z
    .string()
    .min(3, 'El usuario debe tener al menos 3 caracteres')
    .max(50, 'El usuario debe tener menos de 50 caracteres')
    .regex(USERNAME_REGEX, 'Solo letras, números, puntos, guiones y guiones bajos'),
  email: z.string().email('Correo electrónico no válido'),
  extension: z
    .string()
    .max(20, 'La extensión debe tener menos de 20 caracteres')
    .optional()
    .nullable(),
  password: z
    .string()
    .min(8, 'La contraseña debe tener al menos 8 caracteres')
    .max(128, 'La contraseña debe tener menos de 128 caracteres')
    .optional()
    .nullable(),
});

export default UserSelfEditSchema;
