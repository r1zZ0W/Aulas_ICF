/**
 * @fileoverview Custom hook that manages all local form state for creating and
 * editing users in the admin panel.  Includes Zod validation via useZodForm,
 * field-change handlers, and submit orchestration.
 *
 * Validation strategy (powered by useZodForm):
 *   - onBlur per field  → makes that field's error visible immediately.
 *   - onChange on a touched field → clears the error in real time as the user types.
 *   - handleCreateSubmit / handleEditSubmit → call validateAll() first, so clicking
 *     "Guardar" without touching any field still illuminates every required field.
 */
import { useState } from 'react';
import { UserCreateSchema, UserUpdateSchema } from '../../../../schemas/index.js';
import { useZodForm } from '../../../../hooks/useZodForm.js';

// ── Empty form state ──────────────────────────────────────────────────────────

export const EMPTY_CREATE = {
  firstName: '', lastNames: '', username: '', email: '',
  password: '', roleId: ''
};

export const EMPTY_UPDATE = {
  firstName: '', lastNames: '', username: '', email: '',
  roleId: '', password: '', isActive: 'true', departamento: ''
};

// ── Preprocessors ─────────────────────────────────────────────────────────────
// These convert raw form strings to the typed values that UserCreateSchema /
// UserUpdateSchema expect, mirroring what the submit handlers do before parsing.

const createPreprocess = (data) => ({
  ...data,
  roleId: data.roleId ? Number(data.roleId) : undefined,
  departamento: data.departamento || undefined,
});

const updatePreprocess = (data) => ({
  ...data,
  roleId: Number(data.roleId),
  isActive: data.isActive === 'true' || data.isActive === true,
  departamento: data.departamento || undefined,
  password: data.password ? data.password : undefined,
});

// ── Hook ──────────────────────────────────────────────────────────────────────

/**
 * Manages create-form state, edit-form state, and modal open/close state for
 * the Users admin page.
 *
 * @param {{
 *   roles:              Array<{id: number, name: string}>,
 *   createMutation:     import('@tanstack/react-query').UseMutationResult,
 *   updateMutation:     import('@tanstack/react-query').UseMutationResult,
 * }} deps
 *
 * @returns {{
 *   createOpen:         boolean,
 *   editUser:           object | null,
 *   deleteTarget:       object | null,
 *   setDeleteTarget:    (user: object | null) => void,
 *   createForm:         typeof EMPTY_CREATE,
 *   editForm:           typeof EMPTY_UPDATE,
 *   createErrors:       Record<string, string | undefined>,
 *   editErrors:         Record<string, string | undefined>,
 *   openCreate:         () => void,
 *   closeCreate:        () => void,
 *   openEdit:           (user: object) => void,
 *   closeEdit:          () => void,
 *   handleCreateField:  (field: string, value: any) => void,
 *   handleEditField:    (field: string, value: any) => void,
 *   handleCreateBlur:   (field: string) => void,
 *   handleEditBlur:     (field: string) => void,
 *   handleCreateSubmit: () => void,
 *   handleEditSubmit:   () => void,
 * }}
 */
export function useUsersForm({ roles, createMutation, updateMutation }) {
  // ── Modal / target state ────────────────────────────────────────────────────
  const [createOpen, setCreateOpen] = useState(false);
  const [editUser, setEditUser] = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);

  // ── Zod-backed form instances ───────────────────────────────────────────────
  const createZod = useZodForm(EMPTY_CREATE, UserCreateSchema, { preprocess: createPreprocess });
  const editZod   = useZodForm(EMPTY_UPDATE, UserUpdateSchema, { preprocess: updatePreprocess });

  // ── Modal helpers ──────────────────────────────────────────────────────────
  function openCreate() {
    createZod.reset(EMPTY_CREATE);
    setCreateOpen(true);
  }

  function closeCreate() {
    setCreateOpen(false);
    createZod.reset(EMPTY_CREATE);
  }

  /**
   * Opens the edit modal pre-populated with the selected user's data.
   * @param {object} user - The user to edit.
   */
  function openEdit(user) {
    setEditUser(user);
    editZod.reset({
      firstName:    user.firstName,
      lastNames:    user.lastNames,
      username:     user.username ?? '',
      email:        user.email,
      departamento: user.departamento ?? '',
      roleId:       String(
        roles.find((r) => r.name.toUpperCase() === user.roleName?.toUpperCase())?.id ?? ''
      ),
      isActive: String(user.isActive ?? 'true'),
      password: '',
    });
  }

  /** Closes the edit modal and resets its form. */
  function closeEdit() {
    setEditUser(null);
    editZod.reset(EMPTY_UPDATE);
  }

  // ── Field handlers ─────────────────────────────────────────────────────────
  // These are thin wrappers that keep the public API surface identical to what
  // UsersPage / UserFormFields already call.
  const handleCreateField = (field, value) => createZod.handleChange(field, value);
  const handleEditField   = (field, value) => editZod.handleChange(field, value);

  const handleCreateBlur = (field) => createZod.handleBlur(field);
  const handleEditBlur   = (field) => editZod.handleBlur(field);

  // ── Submit handlers ────────────────────────────────────────────────────────
  async function handleCreateSubmit() {
    // validateAll() marks every field as touched and returns false if Zod fails.
    // This illuminates all required-field errors even when the user never touched
    // a single input (direct "Guardar" click on a blank form).
    const isValid = createZod.validateAll();
    if (!isValid) return;

    const payload = {
      ...createZod.formData,
      roleId: createZod.formData.roleId ? Number(createZod.formData.roleId) : undefined,
      departamento: createZod.formData.departamento || undefined,
    };
    try {
      await createMutation.mutateAsync(payload);
      closeCreate();
    } catch (err) {
      // useUsers' onError already toasted; additionally highlight the offending
      // input if the server told us which field caused it (e.g. duplicate email).
      if (err.fieldErrors) createZod.setServerErrors(err.fieldErrors);
    }
  }

  async function handleEditSubmit() {
    const isValid = editZod.validateAll();
    if (!isValid) return;

    const payload = {
      ...editZod.formData,
      roleId: Number(editZod.formData.roleId),
      isActive: editZod.formData.isActive === 'true' || editZod.formData.isActive === true,
      departamento: editZod.formData.departamento || undefined,
      password: editZod.formData.password ? editZod.formData.password : undefined,
    };
    try {
      await updateMutation.mutateAsync({ uuid: editUser.uuid, payload });
      closeEdit();
    } catch (err) {
      // useUsers' onError already toasted; additionally highlight the offending
      // input if the server told us which field caused it (e.g. duplicate email).
      if (err.fieldErrors) editZod.setServerErrors(err.fieldErrors);
    }
  }

  return {
    // modal state
    createOpen,
    editUser,
    deleteTarget,
    setDeleteTarget,
    // form data
    createForm:  createZod.formData,
    editForm:    editZod.formData,
    createErrors: createZod.errors,
    editErrors:   editZod.errors,
    // actions
    openCreate,
    closeCreate,
    openEdit,
    closeEdit,
    handleCreateField,
    handleEditField,
    handleCreateBlur,
    handleEditBlur,
    handleCreateSubmit,
    handleEditSubmit,
  };
}
