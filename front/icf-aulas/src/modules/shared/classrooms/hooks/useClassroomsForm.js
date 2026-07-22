/**
 * @fileoverview Local form and modal state for creating, editing, viewing, and
 * deactivating classrooms. Mirrors the useUsersForm/useResourcesForm useZodForm pattern.
 *
 * Validation strategy (powered by useZodForm):
 *   - onBlur per field  → makes that field's error visible immediately.
 *   - onChange on a touched field → clears the error in real time as the user types.
 *   - handleCreateSubmit / handleEditSubmit → call validateAll() first, so clicking
 *     "Guardar" without touching any field still illuminates every required field.
 *     (Previously this hook did a silent `safeParse` + `return` with no error state at
 *     all — that was the root cause of "no validation shows" for this form.)
 *
 * A SINGLE useZodForm instance is reused for both create and edit — a classroom modal is
 * never in both modes at once, so two instances would just duplicate state/handlers.
 *
 * Children assignment (edit mode):
 *   `form.childUuids` holds the UUIDs of classrooms the current aula should contain.
 *   It is NOT part of ClassroomRequestSchema (children are a separate PUT endpoint), so
 *   ClassroomRequestSchema.safeParse() strips it from the outgoing payload automatically —
 *   we read it straight from zod.formData for the diff below.
 *   On openEdit() it is pre-populated with the current direct children (via getChildren).
 *   handleEditSubmit() diffs against `prevChildUuids` and, when the selection changed,
 *   calls `setChildrenMutation` with the full desired set — a single atomic
 *   PUT /api/v1/classrooms/{uuid}/children handled server-side in one transaction.
 */
import { useState } from 'react';
import { ClassroomRequestSchema } from '../../../../schemas/classroom';
import { useZodForm } from '../../../../hooks/useZodForm';
import { getChildren } from '../../../../utils/classroomTree';

const DEFAULT_FORM = {
  name: '',
  capacity: 20,
  type: 'AULA',
  description: '',
  linkedRoomUuid: null,
  isActive: true,
  childUuids: [],
  roomImageUrl: '',
};

/**
 * @param {{
 *   createMutation:      import('@tanstack/react-query').UseMutationResult,
 *   updateMutation:      import('@tanstack/react-query').UseMutationResult,
 *   setChildrenMutation: import('@tanstack/react-query').UseMutationResult,
 *   allClassrooms:       object[],
 * }} deps
 */
export function useClassroomsForm({
  createMutation,
  updateMutation,
  setChildrenMutation,
  allClassrooms = [],
}) {
  // ── Modal / target state ────────────────────────────────────────────────────
  const [createOpen, setCreateOpen] = useState(false);
  const [editTarget, setEditTarget] = useState(null);
  const [viewTarget, setViewTarget] = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);

  // ── Zod-backed form instance — single instance for both create and edit ────────
  const zod = useZodForm(DEFAULT_FORM, ClassroomRequestSchema);

  // Snapshot of children UUIDs when the edit modal was opened,
  // used to diff against form.childUuids on submit.
  const [prevChildUuids, setPrevChildUuids] = useState([]);

  // ── Modal helpers ───────────────────────────────────────────────────────────
  function openCreate() {
    zod.reset(DEFAULT_FORM);
    setCreateOpen(true);
  }
  function closeCreate() {
    setCreateOpen(false);
    zod.reset(DEFAULT_FORM);
  }

  function openEdit(classroom) {
    const currentChildren = getChildren(classroom.uuid, allClassrooms).map((c) => c.uuid);
    setEditTarget(classroom);
    setPrevChildUuids(currentChildren);
    zod.reset({
      name: classroom.name ?? '',
      capacity: classroom.capacity ?? 1,
      type: classroom.type ?? 'AULA',
      description: classroom.description ?? '',
      linkedRoomUuid: classroom.linkedRoomUuid ?? null,
      isActive: classroom.isActive ?? true,
      childUuids: currentChildren,
      roomImageUrl: classroom.roomImageUrl ?? '',
    });
  }
  function closeEdit() {
    setEditTarget(null);
    zod.reset(DEFAULT_FORM);
  }

  function openDelete(classroom) { setDeleteTarget(classroom); }
  function closeDelete() { setDeleteTarget(null); }

  function openView(classroom) { setViewTarget(classroom); }
  function closeView() { setViewTarget(null); }

  // ── Submit handlers ─────────────────────────────────────────────────────────
  async function handleCreateSubmit() {
    // validateAll() marks every field as touched and returns false if Zod fails.
    // This illuminates all required-field errors even when the user never touched
    // a single input (direct "Crear Aula" click on a default-filled form).
    const isValid = zod.validateAll();
    if (!isValid) return;

    // childUuids isn't part of ClassroomRequestSchema — safeParse strips it automatically.
    const result = ClassroomRequestSchema.safeParse(zod.formData);
    if (!result.success) return; // already confirmed by validateAll(); defensive only

    try {
      await createMutation.mutateAsync(result.data);
      closeCreate();
    } catch {
      // error already surfaced by useApiMutation onError (toast)
    }
  }

  async function handleEditSubmit() {
    const isValid = zod.validateAll();
    if (!isValid) return;

    const result = ClassroomRequestSchema.safeParse(zod.formData);
    if (!result.success) return; // already confirmed by validateAll(); defensive only

    // 1. Update the classroom's own fields
    try {
      await updateMutation.mutateAsync({ uuid: editTarget.uuid, payload: result.data });
    } catch {
      // error already surfaced as a toast — abort without closing
      return;
    }

    // 2. Update children if the selection changed.
    //    Send the full desired set to the backend — it diffs and applies atomically.
    const childUuids = zod.formData.childUuids ?? [];
    const newSet = new Set(childUuids);
    const prevSet = new Set(prevChildUuids ?? []);
    const childrenChanged =
      newSet.size !== prevSet.size || [...newSet].some((u) => !prevSet.has(u));

    if (childrenChanged) {
      try {
        await setChildrenMutation.mutateAsync({
          parentUuid: editTarget.uuid,
          childUuids,
        });
      } catch {
        // setChildrenMutation.onError already: invalidated cache + error toast.
        // Close the modal so the user sees the re-fetched (real) state when they reopen it.
        closeEdit();
        return;
      }
    }

    closeEdit();
  }

  return {
    createOpen,
    editTarget,
    viewTarget,
    deleteTarget,
    form: zod.formData,
    errors: zod.errors,
    openCreate,
    closeCreate,
    openEdit,
    openDelete,
    closeEdit,
    openView,
    closeView,
    setDeleteTarget,
    onField: zod.handleChange,
    onFieldBlur: zod.handleBlur,
    handleCreateSubmit,
    handleEditSubmit,
  };
}
