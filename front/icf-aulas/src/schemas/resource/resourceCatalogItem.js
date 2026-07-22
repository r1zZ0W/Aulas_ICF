/**
 * @fileoverview Validation schema for a single item of the equipment resource catalog
 * (GET /api/v1/resources), as consumed by the classroom-resources "add" picker.
 *
 * Deliberately permissive on `name` (plain string) rather than a strict enum: the catalog
 * picker only needs to list and display whatever equipment exists in the DB, so it must not
 * break parsing if a new equipment type is ever added directly on the backend.
 *
 * `uuid` is the identifier used everywhere the catalog item flows into an allocation request
 * (`resourceUuid` in `ClassroomResourceMutationSchema`) — the backend never exposes the
 * internal numeric `id`.
 */
import { z } from 'zod';

export const ResourceCatalogItemSchema = z.object({
  uuid: z.string().uuid(),
  name: z.string(),
  description: z.string().nullable().optional(),
  quantity: z.number().int().optional(),
});

export default ResourceCatalogItemSchema;
