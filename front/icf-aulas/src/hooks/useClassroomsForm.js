/**
 * @fileoverview Local form and modal state for creating, editing, viewing, and
 * deleting classrooms. Mirrors the useUsersForm pattern.
 */
import { useState } from 'react';
import { ClassroomRequestSchema } from '../schemas/classroom';

const DEFAULT_FORM = {
  name:          '',
  capacity:      20,
  type:          'AULA',
  description:   '',
  linkedRoomUuid: null,
  isActive:      true,
};

/**
 * @param {{
 *   createMutation: import('@tanstack/react-query').UseMutationResult,
 *   updateMutation: import('@tanstack/react-query').UseMutationResult,
 * }} deps
 *
 * @returns {{
 *   createOpen:          boolean,
 *   editTarget:          object | null,
 *   viewTarget:          object | null,
 *   deleteTarget:        object | null,
 *   form:                typeof DEFAULT_FORM,
 *   openCreate:          () => void,
 *   closeCreate:         () => void,
 *   openEdit:            (classroom: object) => void,
 *   closeEdit:           () => void,
 *   openView:            (classroom: object) => void,
 *   closeView:           () => void,
 *   setDeleteTarget:     (classroom: object | null) => void,
 *   onField:             (field: string, value: any) => void,
 *   handleCreateSubmit:  () => Promise<void>,
 *   handleEditSubmit:    () => Promise<void>,
 * }}
 */
export function useClassroomsForm({ createMutation, updateMutation }) {
  // ── Modal / target state ────────────────────────────────────────────────────
  const [createOpen,   setCreateOpen]   = useState(false);
  const [editTarget,   setEditTarget]   = useState(null);
  const [viewTarget,   setViewTarget]   = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);

  // ── Shared form data ────────────────────────────────────────────────────────
  // A single form object is intentional: create and edit modals are never
  // open at the same time, so sharing state avoids double synchronisation.
  const [form, setForm] = useState(DEFAULT_FORM);

  // ── Modal helpers ───────────────────────────────────────────────────────────
  function openCreate() {
    setForm(DEFAULT_FORM);
    setCreateOpen(true);
  }
  function closeCreate() { setCreateOpen(false); }

  function openEdit(classroom) {
    setEditTarget(classroom);
    setForm({
      name:           classroom.name           ?? '',
      capacity:       classroom.capacity       ?? 1,
      type:           classroom.type           ?? 'AULA',
      description:    classroom.description    ?? '',
      linkedRoomUuid: classroom.linkedRoomUuid ?? null,
      isActive:       classroom.isActive       ?? true,
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
      linkedRoomUuid: form.linkedRoomUuid || null, // normalise '' → null
    };
    const result = ClassroomRequestSchema.safeParse(payload);
    if (!result.success) return; // validation error — UI shows inline feedback in future
    try {
      await createMutation.mutateAsync(result.data);
      closeCreate();
    } catch {
      // error already surfaced by useClassrooms onError (toast)
    }
  }

  async function handleEditSubmit() {
    const payload = {
      ...form,
      capacity:       Number(form.capacity),
      linkedRoomUuid: form.linkedRoomUuid || null, // normalise '' → null
    };
    const result = ClassroomRequestSchema.safeParse(payload);
    if (!result.success) return;
    try {
      await updateMutation.mutateAsync({ uuid: editTarget.uuid, payload: result.data });
      closeEdit();
    } catch {
      // error already surfaced by useClassrooms onError (toast)
    }
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
