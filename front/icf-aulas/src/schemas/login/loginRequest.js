/**
 * @fileoverview Validation schema for login requests.
 */
import { z } from 'zod';

/** Rejects the same special-character set previously enforced by validateLoginUsername
 *  (utils/validations.js) — kept here so the rule lives in the single Zod source of truth. */
const NO_SPECIAL_CHARS_REGEX = /[,/\\*+=!?¡¿#%"'$&@{}[\]()|<>~`^]/;

/**
 * Zod schema for validating the user login credentials payload.
 * Mirrors the backend LoginRequest contract (username and password).
 *
 * `username` carries the same rules the pre-migration hand-rolled validator enforced
 * (length 3–50, no special characters) — login intentionally does NOT reuse the stricter
 * @icf.unam.mx / regex rules from UserCreateSchema, since those are creation-time policy,
 * not a login-time constraint. `password` only checks presence (never strength) — a login
 * attempt must be allowed to fail server-side with a generic "bad credentials", not leak
 * password-policy details through client validation.
 */
export const LoginRequestSchema = z.object({
  username: z
    .string()
    .min(3, 'El usuario debe tener al menos 3 caracteres')
    .max(50, 'El usuario no puede exceder los 50 caracteres')
    .refine((v) => !NO_SPECIAL_CHARS_REGEX.test(v), 'No se permiten caracteres especiales'),
  password: z.string().min(1, 'La contraseña es obligatoria'),
});

export default LoginRequestSchema;