/**
 * @fileoverview Validation schema for the ProfilePage FORM (raw UI state), as opposed to
 * {@link UserSelfEditSchema} which validates the network PUT /api/v1/users/me payload.
 * Adds `confirmPassword` — a form-only field with no DTO counterpart — and its cross-field
 * match check, both of which don't belong in the wire schema.
 *
 * `isAdmin` gates the password rules (only admins may change their password from this page)
 * and must be known at schema-build time, hence the factory — mirrors
 * `getSemesterSchema({ isEdit })` in schemas/semester/semesterForm.js.
 */
import { z } from 'zod';
import { USERNAME_REGEX } from './constants.js';

export function getUserSelfEditFormSchema({ isAdmin }) {
  return z
    .object({
      firstName: z.string().trim().min(1, 'El nombre es requerido').max(100, 'El nombre debe tener menos de 100 caracteres'),
      lastNames: z.string().trim().min(1, 'Los apellidos son requeridos').max(100, 'Los apellidos deben tener menos de 100 caracteres'),
      username: z
        .string()
        .trim()
        .min(3, 'El usuario debe tener al menos 3 caracteres')
        .max(50, 'El usuario debe tener menos de 50 caracteres')
        .regex(USERNAME_REGEX, 'Solo letras, números, puntos, guiones y guiones bajos'),
      email: z.string().trim().email('Correo electrónico no válido'),
      extension: z.string().max(20, 'La extensión debe tener menos de 20 caracteres'),
      // Optional: an empty string means "leave unchanged" — only validated when non-empty.
      password: z.string().refine(
        (v) => v === '' || (v.length >= 8 && v.length <= 128),
        'La contraseña debe tener entre 8 y 128 caracteres',
      ),
      confirmPassword: z.string(),
    })
    .superRefine((data, ctx) => {
      if (isAdmin && data.password !== '' && data.password !== data.confirmPassword) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['confirmPassword'],
          message: 'Las contraseñas no coinciden',
        });
      }
    });
}

/**
 * `UserSelfEditRequestDTO` (backend) field names all match this form's 1:1 — no `dtoMap`
 * needed. `confirmPassword` has no backend counterpart, so a server error can never name it;
 * nothing to map.
 */
