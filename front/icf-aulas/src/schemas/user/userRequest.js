/**
 * @fileoverview Validation schemas for user API write requests.
 */
import { z } from 'zod';

/** Institutional email domain restriction matching backend validation. */
const ICF_EMAIL_REGEX = /^[^\s@]+@icf\.unam\.mx$/;

/** Strong-password rule matching backend @Pattern on RegisterRequestDTO. */
const STRONG_PASSWORD_REGEX =
  /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&#^()_\-+=])[A-Za-z\d@$!%*?&#^()_\-+=]{8,128}$/;

/** Username rule matching backend @Pattern on RegisterRequestDTO. */
const USERNAME_REGEX = /^[a-zA-Z0-9][a-zA-Z0-9._-]{2,49}$/;

/** Name/lastname rule (letters, spaces, hyphens). */
const NAME_REGEX = /^[a-zA-ZÀ-ÿ '-]{1,100}$/;

/**
 * Schema for validating user creation requests.
 * Maps to backend RegisterRequestDTO.
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

/**
 * Schema for admin-initiated user updates.
 * Maps to backend UserUpdateRequestDTO.
 */
export const UserUpdateSchema = z.object({
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
  roleId: z.number().int().positive('El rol es requerido'),
  password: z
    .string()
    .min(8, 'La contraseña debe tener al menos 8 caracteres')
    .max(128, 'La contraseña debe tener menos de 128 caracteres')
    .optional()
    .nullable(),
});

/**
 * Schema for the authenticated user's profile update.
 * Matches the backend PUT /api/v1/users/me contract.
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

export default UserCreateSchema;
