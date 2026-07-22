/**
 * @fileoverview Validation schema for the global resources admin dashboard stats.
 * Mirrors the backend's `ResourceStatsDTO { totalTypes, totalUnits }`.
 */
import { z } from 'zod';

export const ResourceStatsSchema = z.object({
  totalTypes: z.number().int(),
  totalUnits: z.number().int(),
});

export default ResourceStatsSchema;
