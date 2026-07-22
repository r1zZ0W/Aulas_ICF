/**
 * @fileoverview Local form and modal state for creating and editing semesters.
 * Mirrors the useUsersForm/useResourcesForm useZodForm pattern.
 *
 * Validation strategy (mirrors semesters-frontend-requests.md §Análisis de implementación):
 *  1. Schema validation: SemesterRequestSchema (format, date existence, end > start).
 *  2. Mode-conditional "no past dates" — lives INSIDE the schema via getSemesterSchema's
 *     `.superRefine()` (see schemas/semester/semesterForm.js), not as an imperative guard
 *     after validateAll(). A post-validateAll() `if` would re-fragment the validation flow:
 *     Zod would report success:true and then an unrelated check would silently block submit.
 *     - create:       startDate and endDate must not be in the past.
 *     - edit active/future (originalEndDate >= today): endDate must not move to the past.
 *     - edit concluded (originalEndDate < today):     no past-date restriction (allow corrections).
 *  NOTE: `isActive` is NEVER sent in the payload; it is backend-derived and read-only.
 */
import { useMemo, useState } from 'react';
import { useZodForm } from '../../../../hooks/useZodForm';
import { getSemesterSchema } from '../../../../schemas/semester/semesterForm.js';

const EMPTY_FORM = { name: '', startDate: '', endDate: '' };

/**
 * @param {{
 *   createMutation: import('@tanstack/react-query').UseMutationResult,
 *   updateMutation: import('@tanstack/react-query').UseMutationResult,
 * }} deps
 *
 * @returns {{
 *   open:         boolean,
 *   mode:         'create' | 'edit',
 *   form:         { name: string, startDate: string, endDate: string },
 *   formErrors:   Record<string, string | undefined>,
 *   editTarget:   object | null,
 *   openCreate:   () => void,
 *   openEdit:     (semester: object) => void,
 *   close:        () => void,
 *   onField:      (field: string, value: string) => void,
 *   onFieldBlur:  (field: string) => void,
 *   handleSubmit: () => Promise<void>,
 * }}
 */
export function useSemestersForm({ createMutation, updateMutation }) {
  const [open, setOpen] = useState(false);
  const [mode, setMode] = useState('create');
  const [editTarget, setEditTarget] = useState(null);

  // Reference date, computed once per render and passed explicitly into the schema — the
  // schema itself never calls `new Date()`, mirroring the backend service's discipline of
  // being "the sole source of the reference date" (see SemesterService.java).
  const today = new Date().toISOString().slice(0, 10);
  const isConcluded = mode === 'edit' && (editTarget?.endDate ?? today) < today;

  // The schema MUST be memoized: calling the factory directly in the hook body would create a
  // brand-new schema instance every render, and useZodForm keys its internal useCallbacks on
  // `schema` identity — an ever-changing reference would reset/re-evaluate validation in a loop.
  const schema = useMemo(
    () => getSemesterSchema({ isEdit: mode === 'edit', isConcluded, today }),
    [mode, isConcluded, today],
  );

  const zod = useZodForm(EMPTY_FORM, schema);

  // ── Modal helpers ─────────────────────────────────────────────────────────────

  function openCreate() {
    zod.reset(EMPTY_FORM);
    setMode('create');
    setOpen(true);
  }

  function openEdit(semester) {
    setEditTarget(semester);
    zod.reset({
      name: semester.name,
      startDate: semester.startDate,
      endDate: semester.endDate,
    });
    setMode('edit');
    setOpen(true);
  }

  function close() {
    setOpen(false);
    zod.reset(EMPTY_FORM);
    // keep editTarget for a moment so the modal animates out correctly
  }

  // ── Submit ────────────────────────────────────────────────────────────────────

  async function handleSubmit() {
    // validateAll() marks every field as touched and returns false if Zod fails — this
    // illuminates all required-field/date-rule errors even when the user never touched a
    // single input (direct "Crear/Guardar" click).
    const isValid = zod.validateAll();
    if (!isValid) return;

    const result = schema.safeParse(zod.formData);
    if (!result.success) return; // already confirmed by validateAll(); defensive only

    try {
      if (mode === 'create') {
        await createMutation.mutateAsync(result.data);
      } else {
        await updateMutation.mutateAsync({ uuid: editTarget.uuid, payload: result.data });
      }
      close();
    } catch {
      // Error already surfaced as a toast by useApiMutation's onError handler.
    }
  }

  return {
    open,
    mode,
    form: zod.formData,
    formErrors: zod.errors,
    editTarget,
    openCreate,
    openEdit,
    close,
    onField: zod.handleChange,
    onFieldBlur: zod.handleBlur,
    handleSubmit,
  };
}
