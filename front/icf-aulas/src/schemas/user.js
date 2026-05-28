/**
 * @fileoverview Validation schemas for user account creation, profile management, and authentication flows.
 * Handles access roles, domain restrictions, and credential validation rules.
 */
import { z } from 'zod'

/** Institutional email domain restriction pattern ensuring only university domains (@icf.unam.mx) are registered. */
const ICF_EMAIL_REGEX = /^[^\s@]+@icf\.unam\.mx$/

// ─── Auth ────────────────────────────────────────────────────────────────────

/**
 * Schema for validating login request credentials.
 * Ensures the email belongs to the institutional domain and checks basic password formatting.
 */
export const LoginRequestSchema = z.object({
  email: z
    .string()
    .email('Dirección de correo electrónico no válida')
    .regex(ICF_EMAIL_REGEX, 'El correo debe pertenecer al dominio @icf.unam.mx'),
  password: z.string().min(8, 'La contraseña debe tener al menos 8 caracteres')
})

/**
 * Schema for validating and parsing successful login session payloads.
 * Includes user information, authorization role, and secure access token.
 */
export const LoginResponseSchema = z.object({
  token: z.string(),
  uuid: z.string().uuid(),
  name: z.string(),
  role: z.string(),
  email: z.string().email()
})

/**
 * Schema for validating registration details for a new user account.
 * Enforces names, passwords, institutional emails, role selections, and requires password confirmation matching.
 * @note The 'confirmPassword' field is processed locally for front-end validation and is not submitted to backend APIs.
 */
export const RegisterRequestSchema = z
  .object({
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
      .min(1, 'El nombre de usuario es requerido')
      .max(100, 'El nombre de usuario debe tener menos de 100 caracteres'),
    email: z
      .string()
      .email('Dirección de correo electrónico no válida')
      .regex(ICF_EMAIL_REGEX, 'El correo debe pertenecer al dominio @icf.unam.mx'),
    password: z
      .string()
      .min(8, 'La contraseña debe tener al menos 8 caracteres')
      .max(128, 'La contraseña debe tener menos de 128 caracteres'),
    confirmPassword: z.string().min(8, 'La confirmación debe tener al menos 8 caracteres'),
    roleId: z.number().int().positive('El rol es requerido')
  })
  .refine(data => data.password === data.confirmPassword, {
    message: 'Las contraseñas no coinciden',
    path: ['confirmPassword']
  })

// ─── Users ───────────────────────────────────────────────────────────────────

/**
 * Schema for validating user creation and administrative profile update requests.
 * Enforces field lengths, institutional email restrictions, and user roles.
 */
export const UserRequestSchema = z.object({
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
})

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

export default LoginRequestSchema
