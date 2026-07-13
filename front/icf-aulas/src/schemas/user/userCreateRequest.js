/**
 * @fileoverview Validation schema for creating a user.
 */
import { z } from 'zod';
import { NAME_REGEX, USERNAME_REGEX, ICF_EMAIL_REGEX, STRONG_PASSWORD_REGEX } from './constants.js';

/**
 * Zod schema for validating user creation payloads.
 * Maps directly to RegisterRequestDTO on the backend.
 */
export const UserCreateSchema = z.object({
  firstName: z
    .string()
    .min(1, 'El nombre es requerido')
    .max(100, 'El nombre debe tener menos de 100 caracteres')
    .regex(NAME_REGEX, 'El nombre solo puede contener letras, espacios y guiones'),
  lastNames: z
    .string()
    .min(1, 'Los apellidos son requeridos')
    .max(100, 'Los apellidos deben tener menos de 100 caracteres')
    .regex(NAME_REGEX, 'Los apellidos solo pueden contener letras, espacios y guiones'),
  username: z
    .string()
    .min(3, 'El usuario debe tener al menos 3 caracteres')
    .max(50, 'El usuario debe tener menos de 50 caracteres')
    .regex(USERNAME_REGEX, 'Solo letras, números, puntos, guiones y guiones bajos'),
  email: z
    .string()
    .email('Correo electrónico no válido')
    .regex(ICF_EMAIL_REGEX, 'El correo debe pertenecer al dominio @icf.unam.mx'),
  password: z
    .string()
    .min(8, 'La contraseña debe tener al menos 8 caracteres')
    .max(128, 'La contraseña debe tener menos de 128 caracteres')
    .regex(
      STRONG_PASSWORD_REGEX,
      'La contraseña debe incluir mayúscula, minúscula, número y carácter especial (@$!%*?&#^()_-+=)'
    ),
  roleId: z.number().int().positive('El rol es requerido').optional(),
});

export default UserCreateSchema;
