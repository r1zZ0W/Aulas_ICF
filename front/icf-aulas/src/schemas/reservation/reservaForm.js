/**
 * @fileoverview Validation schema for the ReservaModal FORM (raw UI state), as opposed to
 * {@link BookingRequestSchema} which validates the network payload sent to
 * `POST /api/v1/reservations/booking`. Field names here mirror useReservaModal's local state
 * (`roomId`, `startLabel`, `endLabel`, `attendees`, `file`, …), not the DTO's wire shape.
 *
 * All validation and coercion lives inside this schema — useZodForm receives no `preprocess`
 * option — so Zod stays the single source of truth for the form (see the plan's
 * "single source of truth" requirement).
 */
import { z } from 'zod';

/** Mirrors the backend's `spring.servlet.multipart.max-file-size=1MB` (application.properties). */
const MAX_FILE_SIZE_BYTES = 1024 * 1024;

export const ReservaFormSchema = z
  .object({
    roomId: z.string().min(1, 'Selecciona un aula'),
    className: z
      .string()
      .trim()
      .min(1, 'Ingresa el nombre de la clase')
      .max(150, 'Máximo 150 caracteres'),
    startLabel: z.string().min(1, 'Selecciona la hora de inicio'),
    endLabel: z.string().min(1, 'Selecciona la hora de fin'),
    // The <select> delivers this as a string through handleChange; coercion lives here (in
    // the schema), not in a useZodForm `preprocess` option.
    //
    // CRITICAL zod v4.4.3 gotcha (verified empirically — not documented, easy to get wrong):
    // a raw `invalid_type` issue (e.g. z.number() rejecting a non-number) is treated as
    // FATAL internally and silently aborts the outer object's `.superRefine()` — it never
    // runs, and cross-field errors like "selectedDays"/"selectedUser" below just don't
    // appear, with no warning. This is exactly the blank-submit scenario (every field empty
    // at once), so it's not a corner case — it defeats the very case validateAll() exists
    // for. z.custom() issues are ALSO fatal for the same reason. Plain `.refine()` issues
    // (code "custom", not "invalid_type") are NOT fatal and never block superRefine.
    // Fix: never let a field surface a raw invalid_type/custom(fatal) issue — always land on
    // a plain `.refine()` instead. For attendees: `.union([string,number]).transform(...)`
    // guarantees the value is always a `number` by the time `.refine()` sees it, so there is
    // no invalid_type path at all.
    attendees: z
      .union([z.string(), z.number()])
      .transform((v) => (v === '' || v == null ? NaN : Number(v)))
      .refine(
        (n) => Number.isInteger(n) && n >= 1,
        'Selecciona el número de alumnos',
      ),
    // z.any().refine() (NOT z.custom / z.instanceof) — see the fatal-issue note above.
    // EMPTY_RESERVA.file starts as `null`; a null-safe guard in each refine avoids both the
    // fatal-issue trap and a TypeError from reading .size/.name off a non-File value.
    file: z
      .any()
      .refine((f) => f instanceof File, 'Adjunta la lista de alumnos (.xlsx)')
      .refine((f) => !(f instanceof File) || f.size <= MAX_FILE_SIZE_BYTES, 'El archivo no debe exceder 1 MB')
      .refine(
        (f) => !(f instanceof File) || f.name.toLowerCase().endsWith('.xlsx'),
        'El archivo debe ser un Excel (.xlsx)',
      ),
    recurring: z.boolean(),
    repeatUntil: z.string().optional(),
    selectedDays: z.array(z.string()),
    reserveForOther: z.boolean(),
    selectedUser: z.any().nullable(),
  })
  .superRefine((data, ctx) => {
    if (data.recurring && data.selectedDays.length === 0) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['selectedDays'],
        message: 'Selecciona al menos un día',
      });
    }
    if (data.reserveForOther && data.selectedUser == null) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['selectedUser'],
        message: 'Selecciona un usuario',
      });
    }
  });

export default ReservaFormSchema;
