/**
 * @fileoverview Validation schemas for academic semesters.
 * Enforces correct format (YYYY-1 or YYYY-2) and validates start and end dates.
 */
import { z } from 'zod'

/**
 * Schema for validating academic semester creation, updates, and responses.
 * Validates semester naming formats, ensures proper date formatting, and verifies that the
 * start date is chronologically before the end date.
 */
export const SemesterSchema = z
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
      .regex(/^\d{4}-\d{2}-\d{2}$/, 'La fecha de inicio debe tener formato YYYY-MM-DD'),
    endDate: z
      .string()
      .regex(/^\d{4}-\d{2}-\d{2}$/, 'La fecha de fin debe tener formato YYYY-MM-DD'),
    isActive: z.boolean().default(true)
  })
  .refine(data => new Date(data.startDate) < new Date(data.endDate), {
    message: 'La fecha de inicio debe ser anterior a la fecha de fin',
    path: ['endDate']
  })

export default SemesterSchema
