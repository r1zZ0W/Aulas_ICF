/**
 * @fileoverview Validation schema for semester creation/update requests.
 */
import { z } from 'zod';

/**
 * Zod schema for validating the payload sent to POST /api/v1/semesters and PUT /api/v1/semesters/{uuid}.
 * Enforces name structure (YYYY-1 or YYYY-2) and validates start/end dates.
 */
export const SemesterRequestSchema = z
  .object({
    name: z
      .string()
      .min(5, 'El nombre del semestre debe tener al menos 5 caracteres')
      .max(20, 'El nombre del semestre debe tener menos de 20 caracteres')
      .regex(
        /^\d{4}-[1-2]$/,
        'El formato del semestre debe ser YYYY-1 o YYYY-2 (ej: 2026-1)'
      ),

    startDate: z
      .string()
      .date('La fecha de inicio es inválida — verifica que el día exista en el calendario (YYYY-MM-DD)'),

    endDate: z
      .string()
      .date('La fecha de fin es inválida — verifica que el día exista en el calendario (YYYY-MM-DD)'),
  })
  .refine(
    (data) => data.startDate < data.endDate,
    {
      message: 'La fecha de fin debe ser posterior a la fecha de inicio',
      path: ['endDate'],
    }
  );

export default SemesterRequestSchema;
