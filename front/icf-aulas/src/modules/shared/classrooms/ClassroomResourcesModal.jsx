import { useEffect, useState } from 'react';
import { X, Plus, Trash2, RotateCw } from 'lucide-react';
import Modal from '../../../components/Modal/Modal';
import Button from '../../../components/Button/Button';
import Input from '../../../components/Input/Input';
import Select from '../../../components/Select/Select';
import LoadingState from '../../../components/LoadingState/LoadingState';
import { useClassroomResources } from './hooks/useClassroomResources';
import { ClassroomResourceMutationSchema } from '../../../schemas/resource';
import './ClassroomResourcesModal.css';

/**
 * Admin modal to add, remove, and update the quantity of equipment resources assigned to a
 * classroom. Persistence is immediate per row (the backend exposes upsert/delete per single
 * resource, not a bulk endpoint) — there is no separate "Guardar" step.
 *
 * All rows are keyed by the resource's public UUID (`resourceUuid`), never its internal
 * numeric id — consistent with UUID-only identification across the whole API.
 *
 * Concurrency / data-integrity guarantees (see plan for the reasoning):
 *  - Each row's quantity edit lives in local `editingQuantities` state, decoupled from the
 *    server-fetched `resources` list, so a background refetch triggered by ANY row's mutation
 *    never overwrites another row's unsaved edit.
 *  - Persistence is onBlur/Enter with a dirty-check + Zod validation (ClassroomResourceMutationSchema
 *    — no hand-rolled numeric checks). On success, error, AND invalid input the local edit is
 *    cleared in a `finally`, so the input always reverts to server truth — never a lingering,
 *    unpersisted value ("UI lie").
 *  - `busyIds` is a map keyed by resourceUuid (not a single primitive): each row's own mutation
 *    disables only that row, so a fast user editing two rows in a row can't have row B
 *    prematurely re-enabled by row A's unrelated mutation finishing.
 *  - The modal cannot be closed (✕ button or Escape) while any mutation is in flight, so a
 *    settling promise's `finally` never touches state on an unmounted component.
 *
 * @param {{ open: boolean, onClose: () => void, classroom: {uuid: string, name: string}|null }} props
 */
export default function ClassroomResourcesModal({ open, onClose, classroom }) {
  const classroomUuid = classroom?.uuid ?? null;

  const {
    catalog,
    catalogLoading,
    catalogError,
    refetchCatalog,
    resources,
    resourcesLoading,
    assignMutation,
    removeMutation,
  } = useClassroomResources(classroomUuid);

  // ── Per-row local state (decoupled from server state — see file header) ──────
  const [editingQuantities, setEditingQuantities] = useState({}); // { [resourceUuid]: raw string }
  const [busyIds, setBusyIds] = useState({});                     // { [resourceUuid]: true }

  // ── "Agregar recurso" row state ───────────────────────────────────────────────
  const [addResourceUuid, setAddResourceUuid] = useState('');
  const [addQuantity, setAddQuantity] = useState('1');
  const [addError, setAddError] = useState('');

  const setQty = (id, v) => setEditingQuantities((prev) => ({ ...prev, [id]: v }));
  const clearQty = (id) => setEditingQuantities((prev) => {
    const next = { ...prev };
    delete next[id];
    return next;
  });
  const setBusy = (id) => setBusyIds((prev) => ({ ...prev, [id]: true }));
  const clearBusy = (id) => setBusyIds((prev) => {
    const next = { ...prev };
    delete next[id];
    return next;
  });

  const hasInFlight = Object.keys(busyIds).length > 0;

  // Requests a close: blocked while any mutation is in flight (so a settling promise's
  // `finally` never touches state after unmount), and — since this is the ONLY path that
  // closes the modal — resets local UI state right here rather than in a useEffect keyed on
  // `open` (avoids a synchronous setState-in-effect render cascade for a transition that is
  // always initiated by this same handler anyway).
  function requestClose() {
    if (hasInFlight) return;
    setEditingQuantities({});
    setBusyIds({});
    setAddResourceUuid('');
    setAddQuantity('1');
    setAddError('');
    onClose();
  }

  // Close on Escape — ignored while a mutation is in flight (see requestClose).
  useEffect(() => {
    if (!open) return;
    const handler = (e) => { if (e.key === 'Escape') requestClose(); };
    document.addEventListener('keydown', handler);
    return () => document.removeEventListener('keydown', handler);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, hasInFlight]);

  if (!classroom) return null;

  // ── Row: commit a pending quantity edit (onBlur / Enter) ─────────────────────
  async function commitQuantity(item) {
    const raw = editingQuantities[item.resourceUuid];
    if (raw === undefined) return; // no edit pending

    const next = Number(raw);
    // Dirty-check — the only hand-written comparison; everything else is delegated to Zod.
    if (next === item.quantity) { clearQty(item.resourceUuid); return; }

    const parsed = ClassroomResourceMutationSchema.safeParse({
      resourceUuid: item.resourceUuid,
      quantity: next,
    });
    if (!parsed.success) { clearQty(item.resourceUuid); return; } // invalid → revert to server truth

    setBusy(item.resourceUuid);
    try {
      await assignMutation.mutateAsync({ classroomUuid, ...parsed.data });
    } catch {
      // toast already emitted by useApiMutation.onError
    } finally {
      clearQty(item.resourceUuid);  // success, error, or invalid → input reflects server truth
      clearBusy(item.resourceUuid); // release only this row, never a shared/global flag
    }
  }

  function handleQuantityKeyDown(e) {
    if (e.key === 'Enter') e.currentTarget.blur(); // delegates the actual commit to onBlur
  }

  // ── Row: remove an allocation ─────────────────────────────────────────────────
  async function handleRemove(item) {
    if (busyIds[item.resourceUuid]) return;
    setBusy(item.resourceUuid);
    try {
      await removeMutation.mutateAsync({ classroomUuid, resourceUuid: item.resourceUuid });
    } catch {
      // toast already emitted by useApiMutation.onError
    } finally {
      clearBusy(item.resourceUuid);
      clearQty(item.resourceUuid); // discard any pending edit on the now-removed row
    }
  }

  // ── "Agregar recurso" row ─────────────────────────────────────────────────────
  const assignedUuids = new Set(resources.map((r) => r.resourceUuid));
  const availableCatalog = catalog.filter((c) => !assignedUuids.has(c.uuid));
  const catalogOptions = availableCatalog.map((c) => ({ value: c.uuid, label: c.name }));

  const catalogSelectPlaceholder = catalogLoading
    ? 'Cargando recursos…'
    : catalogError
      ? 'Error al cargar el catálogo'
      : availableCatalog.length === 0
        ? 'Sin recursos disponibles'
        : 'Selecciona un recurso…';

  const addDisabled =
    !addResourceUuid || catalogLoading || catalogError || availableCatalog.length === 0 || hasInFlight;

  async function handleAdd() {
    setAddError('');
    const parsed = ClassroomResourceMutationSchema.safeParse({
      resourceUuid: addResourceUuid,
      quantity: Number(addQuantity),
    });
    if (!parsed.success) {
      setAddError(parsed.error.issues[0]?.message ?? 'Datos inválidos.');
      return;
    }
    setBusy(parsed.data.resourceUuid);
    try {
      await assignMutation.mutateAsync({ classroomUuid, ...parsed.data });
      setAddResourceUuid('');
      setAddQuantity('1');
    } catch {
      // toast already emitted by useApiMutation.onError
    } finally {
      clearBusy(parsed.data.resourceUuid);
    }
  }

  return (
    <Modal open={open} className="classroom-resources-modal">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="classroom-resources-title"
        className="classroom-resources-modal__inner"
      >
        {/* Header */}
        <header className="classroom-resources-modal__header">
          <div>
            <h2 id="classroom-resources-title" className="classroom-resources-modal__title">
              Recursos del aula
            </h2>
            <p className="classroom-resources-modal__subtitle">{classroom.name}</p>
          </div>
          <button
            type="button"
            className="classroom-resources-modal__close"
            onClick={requestClose}
            disabled={hasInFlight}
            aria-label="Cerrar"
            title={hasInFlight ? 'Espera a que terminen los cambios en curso…' : 'Cerrar'}
            autoFocus
          >
            <X size={24} strokeWidth={2.5} />
          </button>
        </header>

        {/* Body */}
        <div className="classroom-resources-modal__body">
          {resourcesLoading ? (
            <LoadingState message="Cargando recursos del aula…" />
          ) : resources.length === 0 ? (
            <p className="classroom-resources-modal__empty">
              Esta aula no tiene recursos asignados todavía.
            </p>
          ) : (
            <ul className="classroom-resources-modal__list">
              {resources.map((item) => {
                const rowBusy = !!busyIds[item.resourceUuid];
                const displayValue = editingQuantities[item.resourceUuid] ?? String(item.quantity);
                return (
                  <li key={item.resourceUuid} className="classroom-resources-modal__row">
                    <span className="classroom-resources-modal__row-name">{item.resourceName}</span>
                    <Input
                      type="number"
                      min={1}
                      value={displayValue}
                      onChange={(e) => setQty(item.resourceUuid, e.target.value)}
                      onBlur={() => commitQuantity(item)}
                      onKeyDown={handleQuantityKeyDown}
                      disabled={rowBusy}
                      aria-label={`Cantidad de ${item.resourceName}`}
                      className="classroom-resources-modal__row-qty"
                    />
                    <button
                      type="button"
                      className="classroom-resources-modal__row-remove"
                      onClick={() => handleRemove(item)}
                      disabled={rowBusy}
                      aria-label={`Quitar ${item.resourceName}`}
                      title="Quitar"
                    >
                      <Trash2 size={16} />
                    </button>
                  </li>
                );
              })}
            </ul>
          )}

          {/* Agregar recurso */}
          <div className="classroom-resources-modal__add">
            <div className="classroom-resources-modal__add-fields">
              <Select
                value={addResourceUuid}
                onChange={(v) => { setAddResourceUuid(v); setAddError(''); }}
                options={catalogOptions}
                placeholder={catalogSelectPlaceholder}
                disabled={catalogLoading || catalogError || hasInFlight}
                className="classroom-resources-modal__add-select"
              />
              <Input
                type="number"
                min={1}
                value={addQuantity}
                onChange={(e) => { setAddQuantity(e.target.value); setAddError(''); }}
                disabled={hasInFlight}
                aria-label="Cantidad a agregar"
                className="classroom-resources-modal__add-qty"
              />
              <Button
                type="button"
                variant="secondary"
                size="small"
                iconLeft={<Plus size={16} />}
                onClick={handleAdd}
                disabled={addDisabled}
              >
                Agregar
              </Button>
            </div>
            {catalogError && (
              <button
                type="button"
                className="classroom-resources-modal__retry"
                onClick={() => refetchCatalog()}
              >
                <RotateCw size={14} /> Reintentar carga del catálogo
              </button>
            )}
            {addError && <span className="classroom-resources-modal__add-error">{addError}</span>}
          </div>
        </div>

        {/* Footer */}
        <footer className="classroom-resources-modal__footer">
          <Button
            type="button"
            variant="outline"
            size="small"
            onClick={requestClose}
            disabled={hasInFlight}
          >
            {hasInFlight ? 'Guardando…' : 'Cerrar'}
          </Button>
        </footer>
      </div>
    </Modal>
  );
}
