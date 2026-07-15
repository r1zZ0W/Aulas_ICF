/**
 * @fileoverview Validation schema for the classroom-resource mutation the frontend actually
 * sends (assign/update an equipment allocation for a classroom).
 *
 * Deliberately distinct from `ClassroomResourceRequestSchema` (classroomResourceRequest.js),
 * which faithfully mirrors the backend's `ClassroomResourceRequestDTO { classroomId, resourceId,
 * quantity }`. The frontend only ever knows a classroom's public UUID (sent in the URL path),
 * never its internal numeric `classroomId` — and `ClassroomResourceService.save` on the backend
 * ignores `classroomId` in the body anyway (it derives the classroom from the path UUID). Rather
 * than loosen the DTO-mirroring schema — which would misrepresent the real backend contract —
 * this is a dedicated schema for exactly what the client sends: `{ resourceId, quantity }`.
 */
import { z } from 'zod';

export const ClassroomResourceMutationSchema = z.object({
  resourceId: z.number().int().positive('ID de recurso no válido'),
  quantity: z
    .number()
    .int()
    .min(1, 'La cantidad del recurso debe ser al menos 1')
    .default(1),
});

export default ClassroomResourceMutationSchema;
