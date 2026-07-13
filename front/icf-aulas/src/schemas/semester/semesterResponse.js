/**
 * @fileoverview Validation schema for academic semester API responses.
 */
import { z } from 'zod';

/**
 * Zod schema for validating and parsing academic semester details returned by the API.
 * Mirrors SemesterResponseDTO.java.
 */
export const SemesterResponseSchema = z.object({
  uuid:      z.string(),
  name:      z.string(),
  startDate: z.string(),
  endDate:   z.string(),
  isActive:  z.boolean(),
  createdAt: z.string().optional(),
  updatedAt: z.string().optional(),
});

export default SemesterResponseSchema;
