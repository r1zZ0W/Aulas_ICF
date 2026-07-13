/**
 * @fileoverview Generic Zod factory for the backend's PagedResultDTO contract.
 */
import { z } from 'zod';

/**
 * Returns a Zod schema for a paged result whose items are validated by itemSchema.
 *
 * @template T
 * @param {z.ZodType<T>} itemSchema - Schema for a single item in the page.
 * @returns {z.ZodObject} Schema for the full paged result object.
 */
export function PagedResultSchema(itemSchema) {
  return z.object({
    items:         z.array(itemSchema),
    totalElements: z.number(),
    totalPages:    z.number(),
    page:          z.number(),
    size:          z.number(),
    first:         z.boolean(),
    last:          z.boolean(),
  });
}
