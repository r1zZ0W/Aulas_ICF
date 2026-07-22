/**
 * @fileoverview Mode-aware semester FORM schema. Wraps {@link SemesterRequestSchema} (the
 * network payload schema) with the "no past dates" business rules, mirroring
 * SemesterService.validateDates on the backend — see
 * mx.unam.icf.aulas.modules.academic.semesters.app.SemesterService (Java) for the source of
 * truth this schema is kept in lockstep with.
 *
 * All conditional business rules live INSIDE Zod via `.superRefine()` — not as an imperative
 * guard run after validateAll() — so validateAll()/errors remain the single source of truth
 * for the form (a post-validateAll() imperative check would re-fragment the validation flow:
 * Zod would report success:true and then an unrelated `if` would silently block the submit).
 *
 * `today` is passed in explicitly (never read via `new Date()` inside the schema) so the rules
 * stay pure/testable and un-coupled from the system clock — same discipline as the backend
 * service, which is documented as "the sole source of the reference date".
 */
import { z } from 'zod';
import { SemesterRequestSchema } from './semesterRequest.js';

/** @param {string} dateStr - 'YYYY-MM-DD' @param {string} today - 'YYYY-MM-DD' */
function isPastDate(dateStr, today) {
  return dateStr < today;
}

/**
 * Builds a schema with the create/edit-specific no-past-date rules layered on top of
 * {@link SemesterRequestSchema}.
 *
 * Rules:
 *  - create: neither startDate nor endDate may be in the past.
 *  - edit, ongoing/future semester (`!isConcluded`): endDate must not move to the past.
 *    startDate is freely editable (mirrors the backend: correcting an already-passed start
 *    date shouldn't be blocked).
 *  - edit, already-concluded semester (`isConcluded`): no extra rules — historical records
 *    may be freely corrected.
 *
 * @param {{ isEdit: boolean, isConcluded: boolean, today: string }} ctx
 *   isConcluded — the persisted semester's endDate is already before `today`.
 *   today — reference date as 'YYYY-MM-DD', supplied by the caller (no system-clock coupling).
 */
export function getSemesterSchema({ isEdit, isConcluded, today }) {
  return SemesterRequestSchema.superRefine((data, ctx) => {
    if (!isEdit) {
      if (isPastDate(data.startDate, today)) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['startDate'],
          message: 'La fecha de inicio no puede ser en el pasado',
        });
      }
      if (isPastDate(data.endDate, today)) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['endDate'],
          message: 'La fecha de fin no puede ser en el pasado',
        });
      }
    } else if (!isConcluded && isPastDate(data.endDate, today)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['endDate'],
        message: 'No puedes mover la fecha de fin al pasado en un semestre vigente o futuro',
      });
    }
  });
}

export default getSemesterSchema;
