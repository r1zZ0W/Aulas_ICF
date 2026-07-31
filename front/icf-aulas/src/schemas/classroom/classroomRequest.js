/**
 * @fileoverview Validation schema for classroom create / update requests.
 */
import { z } from 'zod';
import { CLASSROOM_TYPES_MAP } from './classroomTypes.js';

/**
 * Zod schema for validating classroom write payloads (create/update).
 * Mirrors ClassroomRequestDTO.java field-by-field.
 */
export const ClassroomRequestSchema = z.object({
  name: z
    .string()
    .min(3, 'El nombre debe tener al menos 3 caracteres')
    .max(100, 'El nombre debe tener menos de 100 caracteres'),

  capacity: z
    .number({ error: 'La capacidad debe ser un número' })
    .int('La capacidad debe ser un número entero')
    .min(2, 'La capacidad debe ser al menos 2')
    .max(500, 'La capacidad máxima admitida es de 500 personas'),

  // NOTE: this project's zod v4.4.3 silently ignores the old `errorMap` option (no error, no
  // warning — the message just never applies, falling back to Zod's generic English message
  // "Invalid option: expected one of ..."). Use the v4 `error` option instead.
  type: z.enum(
    Object.keys(CLASSROOM_TYPES_MAP),
    { error: 'Selecciona un tipo de aula válido' }
  ),

  description: z
    .string()
    .trim()
    .max(500, 'La descripción debe tener menos de 500 caracteres')
    .transform((v) => (v === '' ? null : v))
    .nullable()
    .optional(),

  linkedRoomUuid: z.string().nullable().optional(),

  isActive: z.boolean().default(true),

  // Mirrors the `description` field's empty-string-to-null normalization, plus format/length
  // checks applied via `.refine` (after the transform) so an empty value skips them instead of
  // failing `.url()` outright. 512, not 150: signed URLs (S3/Firebase/Cloudinary) routinely
  // exceed 150 chars once access tokens and hashed paths are included — matches the backend's
  // @Size(max = 512) on ClassroomRequestDTO.roomImageUrl and the VARCHAR(512) column.
  roomImageUrl: z
    .string()
    .trim()
    .transform((v) => (v === '' ? null : v))
    .nullable()
    .optional()
    .refine((v) => !v || v.length <= 512, {
      message: 'El URL de la imagen debe tener menos de 512 caracteres',
    })
    .refine((v) => !v || z.string().safeParse(v).success, {
      message: 'Ingresa un URL de imagen válido (debe iniciar con http:// o https://)',
    }),
});

export default ClassroomRequestSchema;
