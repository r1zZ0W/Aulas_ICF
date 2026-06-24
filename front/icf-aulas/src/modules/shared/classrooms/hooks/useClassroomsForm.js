/**
 * @fileoverview Local form and modal state for creating, editing, viewing, and
 * deactivating classrooms. Mirrors the useUsersForm pattern.
 *
 * Children assignment (edit mode):
 *   `form.childUuids` holds the UUIDs of classrooms the current aula should contain.
 *   On openEdit() it is pre-populated with the current direct children (via getChildren).
 *   handleEditSubmit() diffs against `prevChildUuids` and, when the selection changed,
 *   calls `setChildrenMutation` with the full desired set — a single atomic
 *   PUT /api/v1/classrooms/{uuid}/children handled server-side in one transaction.
 */
import { useState } from 'react';
import { ClassroomRequestSchema } from '../../../../schemas/classroom';
import { getChildren } from '../../../../utils/classroomTree';

const DEFAULT_FORM = {
  name: '',
  capacity: 20,
  type: 'AULA',
  description: '',
  linkedRoomUuid: null,
  isActive: true,
  childUuids: [],
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

  // ── Shared form data ────────────────────────────────────────────────────────
  const [form, setForm] = useState(DEFAULT_FORM);
  // Snapshot of children UUIDs when the edit modal was opened,
  // used to diff against form.childUuids on submit.
  const [prevChildUuids, setPrevChildUuids] = useState([]);

  // ── Modal helpers ───────────────────────────────────────────────────────────
  function openCreate() {
    setForm(DEFAULT_FORM);
    setCreateOpen(true);
  }
  function closeCreate() { setCreateOpen(false); }

  function openEdit(classroom) {
    const currentChildren = getChildren(classroom.uuid, allClassrooms).map((c) => c.uuid);
    setEditTarget(classroom);
    setPrevChildUuids(currentChildren);
    setForm({
      name: classroom.name ?? '',
      capacity: classroom.capacity ?? 1,
      type: classroom.type ?? 'AULA',
      description: classroom.description ?? '',
      linkedRoomUuid: classroom.linkedRoomUuid ?? null,
      isActive: classroom.isActive ?? true,
      childUuids: currentChildren,
    });
  }
  function closeEdit() { setEditTarget(null); }

  function openDelete(classroom) { setDeleteTarget(classroom); }
  function closeDelete() { setDeleteTarget(null); }

  function openView(classroom) { setViewTarget(classroom); }
  function closeView() { setViewTarget(null); }

  // ── Field handler ───────────────────────────────────────────────────────────
  function onField(field, value) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  // ── Submit handlers ─────────────────────────────────────────────────────────
  async function handleCreateSubmit() {
    const payload = {
      ...form,
      capacity: Number(form.capacity),
      linkedRoomUuid: form.linkedRoomUuid || null,
    };
    const result = ClassroomRequestSchema.safeParse(payload);
    if (!result.success) return;
    try {
      await createMutation.mutateAsync(result.data);
      closeCreate();
    } catch {
      // error already surfaced by useApiMutation onError (toast)
    }
  }

  async function handleEditSubmit() {
    const payload = {
      ...form,
      capacity: Number(form.capacity),
      linkedRoomUuid: form.linkedRoomUuid || null,
    };
    const result = ClassroomRequestSchema.safeParse(payload);
    if (!result.success) return;

    // 1. Update the classroom's own fields
    try {
      await updateMutation.mutateAsync({ uuid: editTarget.uuid, payload: result.data });
    } catch {
      // error already surfaced as a toast — abort without closing
      return;
    }

    // 2. Update children if the selection changed.
    //    Send the full desired set to the backend — it diffs and applies atomically.
    const newSet = new Set(form.childUuids ?? []);
    const prevSet = new Set(prevChildUuids ?? []);
    const childrenChanged =
      newSet.size !== prevSet.size || [...newSet].some((u) => !prevSet.has(u));

    if (childrenChanged) {
      try {
        await setChildrenMutation.mutateAsync({
          parentUuid: editTarget.uuid,
          childUuids: form.childUuids ?? [],
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
    form,
    openCreate,
    closeCreate,
    openEdit,
    openDelete,
    closeEdit,
    openView,
    closeView,
    setDeleteTarget,
    onField,
    handleCreateSubmit,
    handleEditSubmit,
  };
}
