/**
 * @fileoverview Validation schema for creating/updating a global equipment resource.
 * Mirrors the backend's `ResourceRequestDTO { name, description, quantity }`.
 */
import { z } from 'zod';

/**
 * `quantity` uses `z.coerce.number()` deliberately: the quantity `<input type="number">`
 * delivers its value as a string through the change handler, and a plain `z.number()` would
 * reject that string outright ("expected number, received string") before coercion ever runs.
 *
 * NOTE: this project's zod v4.4.3 silently ignores the old `invalid_type_error` param (no
 * error, no warning — the message just never applies, falling back to Zod's generic English
 * message). Use the v4 `error` option instead.
 */
export const ResourceRequestSchema = z.object({
  name: z.string()
    .min(1, 'El nombre es obligatorio')
    .max(50, 'El nombre debe tener como máximo 50 caracteres'),
  description: z.string()
    .trim()
    .max(255, 'La descripción debe tener como máximo 255 caracteres')
    .transform((v) => (v === '' ? null : v))
    .nullable()
    .optional(),
  quantity: z.coerce.number({ error: 'La cantidad debe ser un número' })
    .int('La cantidad debe ser un número entero')
    .min(1, 'La cantidad debe ser al menos 1'),
});

export default ResourceRequestSchema;
