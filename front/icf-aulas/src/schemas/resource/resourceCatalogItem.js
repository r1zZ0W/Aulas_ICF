/**
 * @fileoverview Validation schema for a single item of the equipment resource catalog
 * (GET /api/v1/resources), as consumed by the classroom-resources "add" picker.
 *
 * Deliberately permissive on `name` (plain string) rather than the strict 4-value enum used by
 * `ResourceSchema` (resource.js) for resource CRUD forms: the catalog picker only needs to list
 * and display whatever equipment exists in the DB, so it must not break parsing if a new
 * equipment type is ever added directly on the backend.
 */
import { z } from 'zod';

export const ResourceCatalogItemSchema = z.object({
  id: z.number().int(),
  name: z.string(),
  description: z.string().nullable().optional(),
});

export default ResourceCatalogItemSchema;
