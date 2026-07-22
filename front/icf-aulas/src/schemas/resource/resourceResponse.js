/**
 * @fileoverview Validation schema for a global equipment resource returned by the API.
 * Mirrors the backend's `ResourceResponseDTO { uuid, name, description, quantity }`.
 */
import { z } from 'zod';

export const ResourceResponseSchema = z.object({
  uuid: z.string().uuid(),
  name: z.string(),
  description: z.string().nullable().optional(),
  quantity: z.number().int(),
});

export default ResourceResponseSchema;
