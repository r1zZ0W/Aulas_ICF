/**
 * @fileoverview Local form and modal state for creating and editing semesters.
 * Mirrors the useUsersForm pattern: plain useState + Zod safeParse + inline errors.
 *
 * Validation strategy (mirrors semesters-frontend-requests.md §Análisis de implementación):
 *  1. Schema validation: SemesterRequestSchema (format, date existence, end > start).
 *  2. Mode-conditional "no past dates":
 *     - create:       startDate and endDate must not be in the past.
 *     - edit active/future (originalEndDate >= today): endDate must not move to the past.
 *     - edit concluded (originalEndDate < today):     no past-date restriction (allow corrections).
 *  NOTE: `isActive` is NEVER sent in the payload; it is backend-derived and read-only.
 */
import { useState } from 'react';
import { SemesterRequestSchema } from '../../../../schemas/semester';

const EMPTY_FORM = { name: '', startDate: '', endDate: '' };

/** Flattens a ZodError into a { field: firstMessage } map. */
function parseZodErrors(zodError) {
  const out = {};
  for (const issue of zodError.issues ?? []) {
    const key = issue.path[0];
    if (key && !out[key]) out[key] = issue.message;
  }
  return out;
}

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
 *   handleSubmit: () => Promise<void>,
 * }}
 */
export function useSemestersForm({ createMutation, updateMutation }) {
  const [open, setOpen] = useState(false);
  const [mode, setMode] = useState('create');
  const [editTarget, setEditTarget] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [formErrors, setFormErrors] = useState({});

  // ── Modal helpers ─────────────────────────────────────────────────────────────

  function openCreate() {
    setForm(EMPTY_FORM);
    setFormErrors({});
    setMode('create');
    setOpen(true);
  }

  function openEdit(semester) {
    setEditTarget(semester);
    setForm({
      name: semester.name,
      startDate: semester.startDate,
      endDate: semester.endDate,
    });
    setFormErrors({});
    setMode('edit');
    setOpen(true);
  }

  function close() {
    setOpen(false);
    setFormErrors({});
    // keep editTarget / form for a moment so the modal animates out correctly
  }

  // ── Field handler ─────────────────────────────────────────────────────────────

  function onField(field, value) {
    setForm((prev) => ({ ...prev, [field]: value }));
    setFormErrors((prev) => ({ ...prev, [field]: undefined }));
  }

  // ── Submit ────────────────────────────────────────────────────────────────────

  async function handleSubmit() {
    // 1. Schema validation (format + calendar date existence + end > start)
    const result = SemesterRequestSchema.safeParse(form);
    if (!result.success) {
      setFormErrors(parseZodErrors(result.error));
      return;
    }

    // 2. Mode-conditional "no past dates" (mirrors backend rules)
    const today = new Date().toISOString().slice(0, 10);

    if (mode === 'create') {
      if (form.startDate < today) {
        setFormErrors((e) => ({ ...e, startDate: 'La fecha de inicio no puede ser en el pasado' }));
        return;
      }
      if (form.endDate < today) {
        setFormErrors((e) => ({ ...e, endDate: 'La fecha de fin no puede ser en el pasado' }));
        return;
      }
    } else {
      // Edit: the "no past endDate" rule only applies to ongoing/future semesters.
      // Concluded semesters (originalEndDate < today) may be freely corrected.
      const originalEndDate = editTarget?.endDate ?? today;
      const isConcluded = originalEndDate < today;
      if (!isConcluded && form.endDate < today) {
        setFormErrors((e) => ({
          ...e,
          endDate: 'No puedes mover la fecha de fin al pasado en un semestre vigente o futuro',
        }));
        return;
      }
    }

    // 3. Call the appropriate mutation
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
    form,
    formErrors,
    editTarget,
    openCreate,
    openEdit,
    close,
    onField,
    handleSubmit,
  };
}
