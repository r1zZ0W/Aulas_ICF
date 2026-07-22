/**
 * @fileoverview Validation schema for the classroom-resource mutation the frontend actually
 * sends (assign/update an equipment allocation for a classroom).
 *
 * Mirrors the backend's `ClassroomResourceRequestDTO { resourceUuid, quantity }`. The classroom
 * itself is identified by the path UUID of the enclosing endpoint
 * (`/api/v1/classrooms/{classroomUuid}/resources`), never by a field in this body — and the
 * equipment resource is identified by its public `resourceUuid`, consistent with UUID-only
 * identification across the whole API (the backend never exposes the internal numeric id).
 */
import { z } from 'zod';

export const ClassroomResourceMutationSchema = z.object({
  resourceUuid: z.string().uuid('UUID de recurso no válido'),
  quantity: z
    .number()
    .int()
    .min(1, 'La cantidad del recurso debe ser al menos 1')
    .default(1),
});

export default ClassroomResourceMutationSchema;
