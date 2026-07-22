/**
 * @fileoverview Validation schema for the ReasignarModal FORM (raw UI state) — the room/time
 * fields edited when rescheduling an existing reservation. Distinct from
 * {@link ReassignRequestSchema}, which validates the network PATCH payload (partial-update
 * semantics: both fields optional, only the changed one is sent).
 */
import { z } from 'zod';

export const ReasignFormSchema = z.object({
  roomId: z.string().min(1, 'Selecciona un aula'),
  startLabel: z.string().min(1, 'Selecciona la hora de inicio'),
  endLabel: z.string().min(1, 'Selecciona la hora de fin'),
});

export default ReasignFormSchema;
