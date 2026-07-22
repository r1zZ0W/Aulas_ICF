/**
 * @fileoverview Custom hook that manages all local form state for creating and editing
 * global equipment resources in the admin panel. Uses useZodForm for real-time validation.
 *
 * Validation strategy (powered by useZodForm):
 *   - onBlur per field  → makes that field's error visible immediately.
 *   - onChange on a touched field → clears the error in real time as the user types.
 *   - handleCreateSubmit / handleEditSubmit → call validateAll() first, so clicking
 *     "Guardar" without touching any field still illuminates every required field.
 */
import { useState } from 'react';
import { ResourceRequestSchema } from '../../../../schemas/index.js';
import { useZodForm } from '../../../../hooks/useZodForm.js';

// ── Empty form state ──────────────────────────────────────────────────────────

export const EMPTY_FORM = {
  name: '',
  description: '',
  quantity: 1
};

// ── Hook ──────────────────────────────────────────────────────────────────────

/**
 * Manages create-form state, edit-form state, and modal open/close state for the
 * "Gestión de Recursos" admin page.
 *
 * ResourceRequestSchema already uses z.coerce.number() for `quantity`, so raw
 * string values from the <input type="number"> are handled transparently — no
 * preprocess function is needed here.
 *
 * @param {{
 *   createMutation: import('@tanstack/react-query').UseMutationResult,
 *   updateMutation: import('@tanstack/react-query').UseMutationResult,
 * }} deps
 *
 * @returns {{
 *   createOpen:         boolean,
 *   editResource:       object | null,
 *   deleteTarget:       object | null,
 *   setDeleteTarget:    (resource: object | null) => void,
 *   createForm:         typeof EMPTY_FORM,
 *   editForm:           typeof EMPTY_FORM,
 *   createErrors:       Record<string, string | undefined>,
 *   editErrors:         Record<string, string | undefined>,
 *   openCreate:         () => void,
 *   closeCreate:        () => void,
 *   openEdit:           (resource: object) => void,
 *   closeEdit:          () => void,
 *   handleCreateField:  (field: string, value: any) => void,
 *   handleEditField:    (field: string, value: any) => void,
 *   handleCreateBlur:   (field: string) => void,
 *   handleEditBlur:     (field: string) => void,
 *   handleCreateSubmit: () => void,
 *   handleEditSubmit:   () => void,
 * }}
 */
export function useResourcesForm({ createMutation, updateMutation }) {
  // ── Modal / target state ────────────────────────────────────────────────────
  const [createOpen, setCreateOpen] = useState(false);
  const [editResource, setEditResource] = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);

  // ── Zod-backed form instances ───────────────────────────────────────────────
  const createZod = useZodForm(EMPTY_FORM, ResourceRequestSchema);
  const editZod = useZodForm(EMPTY_FORM, ResourceRequestSchema);

  // ── Modal helpers ──────────────────────────────────────────────────────────
  function openCreate() {
    createZod.reset(EMPTY_FORM);
    setCreateOpen(true);
  }

  function closeCreate() {
    setCreateOpen(false);
    createZod.reset(EMPTY_FORM);
  }

  /**
   * Opens the edit modal pre-populated with the selected resource's data.
   * @param {object} resource - The resource to edit.
   */
  function openEdit(resource) {
    setEditResource(resource);
    editZod.reset({
      name: resource.name,
      description: resource.description ?? '',
      quantity: resource.quantity,
    });
  }

  /** Closes the edit modal and resets its form. */
  function closeEdit() {
    setEditResource(null);
    editZod.reset(EMPTY_FORM);
  }

  // ── Field handlers ─────────────────────────────────────────────────────────
  const handleCreateField = (field, value) => createZod.handleChange(field, value);
  const handleEditField = (field, value) => editZod.handleChange(field, value);

  const handleCreateBlur = (field) => createZod.handleBlur(field);
  const handleEditBlur = (field) => editZod.handleBlur(field);

  // ── Submit handlers ────────────────────────────────────────────────────────
  async function handleCreateSubmit() {
    const isValid = createZod.validateAll();
    if (!isValid) return;

    try {
      await createMutation.mutateAsync(createZod.formData);
      closeCreate();
    } catch {
      // error already handled by useResources onError (toast)
    }
  }

  async function handleEditSubmit() {
    const isValid = editZod.validateAll();
    if (!isValid) return;

    try {
      await updateMutation.mutateAsync({ uuid: editResource.uuid, payload: editZod.formData });
      closeEdit();
    } catch {
      // error already handled by useResources onError (toast)
    }
  }

  return {
    // modal state
    createOpen,
    editResource,
    deleteTarget,
    setDeleteTarget,
    // form data
    createForm: createZod.formData,
    editForm: editZod.formData,
    createErrors: createZod.errors,
    editErrors: editZod.errors,
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
