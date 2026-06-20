/**
 * @fileoverview Local form and modal state for creating, editing, viewing, and
 * deactivating classrooms. Mirrors the useUsersForm pattern.
 *
 * Children assignment (edit mode):
 *   `form.childUuids` holds the UUIDs of classrooms the current aula should contain.
 *   On openEdit() it is pre-populated with the current direct children (via getChildren).
 *   handleEditSubmit() diffs against `prevChildUuids` and calls `setChildrenMutation`
 *   when the selection changed, using sequential PUTs (interino — see useClassrooms.js).
 */
import { useState } from 'react';
import { ClassroomRequestSchema } from '../schemas/classroom';
import { getChildren } from '../utils/classroomTree';

const DEFAULT_FORM = {
  name:           '',
  capacity:       20,
  type:           'AULA',
  description:    '',
  linkedRoomUuid: null,
  isActive:       true,
  childUuids:     [],
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
  const [createOpen,    setCreateOpen]    = useState(false);
  const [editTarget,    setEditTarget]    = useState(null);
  const [viewTarget,    setViewTarget]    = useState(null);
  const [deleteTarget,  setDeleteTarget]  = useState(null);

  // ── Shared form data ────────────────────────────────────────────────────────
  const [form,          setForm]          = useState(DEFAULT_FORM);
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
      name:           classroom.name           ?? '',
      capacity:       classroom.capacity       ?? 1,
      type:           classroom.type           ?? 'AULA',
      description:    classroom.description    ?? '',
      linkedRoomUuid: classroom.linkedRoomUuid ?? null,
      isActive:       classroom.isActive       ?? true,
      childUuids:     currentChildren,
    });
  }
  function closeEdit() { setEditTarget(null); }

  function openView(classroom) { setViewTarget(classroom); }
  function closeView()         { setViewTarget(null); }

  // ── Field handler ───────────────────────────────────────────────────────────
  function onField(field, value) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  // ── Submit handlers ─────────────────────────────────────────────────────────
  async function handleCreateSubmit() {
    const payload = {
      ...form,
      capacity:       Number(form.capacity),
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
      capacity:       Number(form.capacity),
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

    // 2. Update children if the selection changed (interino sequential PUTs)
    const newSet  = new Set(form.childUuids ?? []);
    const prevSet = new Set(prevChildUuids ?? []);
    const toAdd   = (form.childUuids ?? [])
      .filter((u) => !prevSet.has(u))
      .map((u) => allClassrooms.find((c) => c.uuid === u))
      .filter(Boolean);
    const toRemove = (prevChildUuids ?? [])
      .filter((u) => !newSet.has(u))
      .map((u) => allClassrooms.find((c) => c.uuid === u))
      .filter(Boolean);

    if (toAdd.length > 0 || toRemove.length > 0) {
      try {
        await setChildrenMutation.mutateAsync({
          parentUuid: editTarget.uuid,
          toAdd,
          toRemove,
        });
      } catch {
        // setChildrenMutation.onError already: invalidated cache + critical toast.
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
    closeEdit,
    openView,
    closeView,
    setDeleteTarget,
    onField,
    handleCreateSubmit,
    handleEditSubmit,
  };
}
