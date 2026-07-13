/**
 * @fileoverview Validation schema for login requests.
 */
import { z } from 'zod';

/**
 * Zod schema for validating the user login credentials payload.
 * Mirrors the backend LoginRequest contract (username and password).
 */
export const LoginRequestSchema = z.object({
  username: z.string().min(1, 'El usuario es requerido'),
  password: z.string().min(1, 'La contraseña es requerida'),
});

export default LoginRequestSchema;