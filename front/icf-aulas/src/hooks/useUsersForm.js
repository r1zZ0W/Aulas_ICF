/**
 * @fileoverview Custom hook that manages all local form state for creating and
 * editing users in the admin panel.  Includes Zod validation, field-change
 * handlers, and submit orchestration.
 */
import { useState } from 'react';
import { UserCreateSchema, UserUpdateSchema } from '../schemas/index.js';

// ── Empty form state ──────────────────────────────────────────────────────────

export const EMPTY_CREATE = {
  firstName: '', lastNames: '', username: '', email: '',
  password: '', departamento: '', roleId: '',
};

export const EMPTY_UPDATE = {
  firstName: '', lastNames: '', username: '', email: '',
  departamento: '', roleId: '', isActive: true,
};

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Flattens a ZodError into a { field: firstMessage } map. */
function parseZodErrors(zodError) {
  const out = {};
  for (const issue of zodError.issues ?? []) {
    const key = issue.path[0];
    if (key && !out[key]) out[key] = issue.message;
  }
  return out;
}

// ── Hook ──────────────────────────────────────────────────────────────────────

/**
 * Manages create-form state, edit-form state, and modal open/close state for
 * the Users admin page.
 *
 * @param {{
 *   roles:              Array<{id: number, name: string}>,
 *   createMutation:     import('@tanstack/react-query').UseMutationResult,
 *   updateMutation:     import('@tanstack/react-query').UseMutationResult,
 * }} deps - Mutations and roles list from useUsers().
 *
 * @returns {{
 *   createOpen:         boolean,
 *   editUser:           object | null,
 *   deleteTarget:       object | null,
 *   createForm:         typeof EMPTY_CREATE,
 *   editForm:           typeof EMPTY_UPDATE,
 *   formErrors:         Record<string, string | undefined>,
 *   openCreate:         () => void,
 *   closeCreate:        () => void,
 *   openEdit:           (user: object) => void,
 *   closeEdit:          () => void,
 *   setDeleteTarget:    (user: object | null) => void,
 *   handleCreateField:  (field: string, value: any) => void,
 *   handleEditField:    (field: string, value: any) => void,
 *   handleCreateSubmit: () => void,
 *   handleEditSubmit:   () => void,
 * }}
 */
export function useUsersForm({ roles, createMutation, updateMutation }) {
  // ── Modal / target state ────────────────────────────────────────────────────
  const [createOpen, setCreateOpen] = useState(false);
  const [editUser, setEditUser] = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);

  // ── Form data & validation errors ──────────────────────────────────────────
  const [createForm, setCreateForm] = useState(EMPTY_CREATE);
  const [editForm, setEditForm] = useState(EMPTY_UPDATE);
  const [formErrors, setFormErrors] = useState({});

  // ── Modal helpers ──────────────────────────────────────────────────────────
  function openCreate() {
    setCreateForm(EMPTY_CREATE);
    setFormErrors({});
    setCreateOpen(true);
  }

  function closeCreate() {
    setCreateOpen(false);
    setFormErrors({});
  }

  /**
   * Opens the modal of the edit user form.
   * @param {object} user - The user to edit.
   */
  function openEdit(user) {
    setEditUser(user);
    setEditForm({
      firstName: user.firstName,
      lastNames: user.lastNames,
      username: user.username ?? '',
      email: user.email,
      departamento: user.departamento ?? '',
      roleId: String(
        roles.find((r) => r.name.toUpperCase() === user.roleName?.toUpperCase())?.id ?? ''
      ),
      isActive: user.isActive,
    });
    setFormErrors({});
  }

  /**
   * Closes the modal of the edit user form.
   */
  function closeEdit() {
    setEditUser(null);
    setFormErrors({});
  }

  // ── Field handlers ─────────────────────────────────────────────────────────
  function handleCreateField(field, value) {
    setCreateForm((f) => ({ ...f, [field]: value }));
    setFormErrors((e) => ({ ...e, [field]: undefined }));
  }

  function handleEditField(field, value) {
    setEditForm((f) => ({ ...f, [field]: value }));
    setFormErrors((e) => ({ ...e, [field]: undefined }));
  }

  // ── Submit handlers ────────────────────────────────────────────────────────
  async function handleCreateSubmit() {
    const payload = {
      ...createForm,
      roleId: createForm.roleId ? Number(createForm.roleId) : undefined,
      departamento: createForm.departamento || undefined,
    };
    const result = UserCreateSchema.safeParse(payload);
    if (!result.success) {
      setFormErrors(parseZodErrors(result.error));
      return;
    }
    try {
      await createMutation.mutateAsync(result.data);
      closeCreate();
    } catch {
      // error already handled by useUsers onError (toast)
    }
  }

  async function handleEditSubmit() {
    const payload = {
      ...editForm,
      roleId: Number(editForm.roleId),
      isActive: editForm.isActive === 'true' || editForm.isActive === true,
      departamento: editForm.departamento || undefined,
    };
    const result = UserUpdateSchema.safeParse(payload);
    if (!result.success) {
      setFormErrors(parseZodErrors(result.error));
      return;
    }
    try {
      await updateMutation.mutateAsync({ uuid: editUser.uuid, payload: result.data });
      closeEdit();
    } catch {
      // error already handled by useUsers onError (toast)
    }
  }

  return {
    // modal state
    createOpen,
    editUser,
    deleteTarget,
    setDeleteTarget,
    // form data
    createForm,
    editForm,
    formErrors,
    // actions
    openCreate,
    closeCreate,
    openEdit,
    closeEdit,
    handleCreateField,
    handleEditField,
    handleCreateSubmit,
    handleEditSubmit,
  };
}
