/**
 * @fileoverview Split button that shows the active semester in the Classrooms page header.
 *
 * Split-button design (resolves the "create vs edit" deadlock):
 *  - PRIMARY action:
 *    · Has active semester  → shows its name + Activo badge; click → openEdit(activeSemester)
 *    · No active semester   → shows "Crear semestre"; click → openCreate()
 *      (NEVER forces edit when history exists — admin must always be able to create new periods)
 *  - CARET action (admin only) → opens a dropdown with ALL semesters + their status badge,
 *    each opening in edit mode. Includes an explicit "Crear nuevo semestre" item at the bottom.
 *  - Non-admin: shows the active semester label as read-only (no caret, no editing).
 *
 * @param {{ isAdmin: boolean }} props
 */
import { useState, useRef, useEffect } from 'react';
import { CalendarDays, ChevronDown, Plus, Pencil } from 'lucide-react';

import { useSemesters } from '../../../hooks/useSemesters';
import { useSemestersForm } from '../../../hooks/useSemestersForm';
import {
  deriveSemesterStatus,
  SEMESTER_STATUS_LABEL,
  SEMESTER_STATUS_BADGE_VARIANT,
} from '../../../utils/semester';
import Badge from '../../../components/Badge/Badge';
import FormModal from '../../../components/FormModal/FormModal';
import SemesterFormFields from './SemesterFormFields';

import './ActiveSemesterButton.css';

export default function ActiveSemesterButton({ isAdmin }) {
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef(null);

  // ── Server state ──────────────────────────────────────────────────────────────
  const { activeSemester, semesters, createMutation, updateMutation } = useSemesters();

  // ── Form / modal state ────────────────────────────────────────────────────────
  const {
    open, mode, form, formErrors, editTarget,
    openCreate, openEdit, close, onField, handleSubmit,
  } = useSemestersForm({ createMutation, updateMutation });

  // ── Close dropdown on outside click ──────────────────────────────────────────
  useEffect(() => {
    if (!menuOpen) return;
    function handleOutside(e) {
      if (!menuRef.current?.contains(e.target)) setMenuOpen(false);
    }
    document.addEventListener('mousedown', handleOutside);
    return () => document.removeEventListener('mousedown', handleOutside);
  }, [menuOpen]);

  // ── Derived display values ────────────────────────────────────────────────────
  const hasActive    = !!activeSemester;
  const activeStatus = hasActive ? deriveSemesterStatus(activeSemester) : null;
  const primaryLabel = hasActive ? `Semestre: ${activeSemester.name}` : 'Crear semestre';

  // ── Handlers ──────────────────────────────────────────────────────────────────
  function handlePrimaryClick() {
    if (!isAdmin) return;
    if (hasActive) openEdit(activeSemester);
    else           openCreate();
  }

  function handleMenuEdit(semester) {
    setMenuOpen(false);
    openEdit(semester);
  }

  function handleMenuCreate() {
    setMenuOpen(false);
    openCreate();
  }

  // ── Render ────────────────────────────────────────────────────────────────────
  return (
    <>
      <div className="semester-btn" ref={menuRef}>
        {/* ── Primary action ── */}
        <button
          type="button"
          className={`semester-btn__main${!isAdmin ? ' semester-btn__main--readonly' : ''}`}
          onClick={handlePrimaryClick}
          title={
            !isAdmin        ? undefined :
            hasActive       ? `Editar ${activeSemester.name}` :
                              'Crear nuevo semestre'
          }
        >
          {hasActive ? <CalendarDays size={15} /> : <Plus size={15} />}
          <span>{primaryLabel}</span>
          {activeStatus && (
            <Badge variant={SEMESTER_STATUS_BADGE_VARIANT[activeStatus]}>
              {SEMESTER_STATUS_LABEL[activeStatus]}
            </Badge>
          )}
          {isAdmin && hasActive && <Pencil size={13} className="semester-btn__edit-icon" />}
        </button>

        {/* ── Caret (admin only) ── */}
        {isAdmin && (
          <button
            type="button"
            className="semester-btn__caret"
            onClick={() => setMenuOpen((o) => !o)}
            aria-label="Ver todos los semestres"
            aria-expanded={menuOpen}
          >
            <ChevronDown size={15} />
          </button>
        )}

        {/* ── Dropdown menu ── */}
        {isAdmin && menuOpen && (
          <div className="semester-btn__menu" role="menu">
            <div className="semester-btn__menu-header">Semestres</div>

            {semesters.length === 0 && (
              <div className="semester-btn__menu-empty">No hay semestres registrados</div>
            )}

            {semesters.map((s) => {
              const st = deriveSemesterStatus(s);
              return (
                <button
                  key={s.uuid}
                  type="button"
                  className="semester-btn__menu-item"
                  onClick={() => handleMenuEdit(s)}
                  role="menuitem"
                >
                  <span>{s.name}</span>
                  <Badge variant={SEMESTER_STATUS_BADGE_VARIANT[st]}>
                    {SEMESTER_STATUS_LABEL[st]}
                  </Badge>
                </button>
              );
            })}

            <div className="semester-btn__menu-divider" />

            <button
              type="button"
              className="semester-btn__menu-item semester-btn__menu-item--create"
              onClick={handleMenuCreate}
              role="menuitem"
            >
              <Plus size={14} />
              <span>Crear nuevo semestre</span>
            </button>
          </div>
        )}
      </div>

      {/* ── Form modal (admin only) ── */}
      {isAdmin && (
        <FormModal
          open={open}
          onClose={close}
          title={mode === 'create' ? 'Nuevo Semestre' : 'Editar Semestre'}
          subtitle={
            mode === 'create'
              ? 'Define el nombre y las fechas del semestre.'
              : (editTarget?.name ?? '')
          }
          submitLabel={mode === 'create' ? 'Crear Semestre' : 'Guardar cambios'}
          submitIcon={mode === 'create' ? <Plus size={18} /> : undefined}
          loading={createMutation.isPending || updateMutation.isPending}
          onSubmit={handleSubmit}
        >
          <SemesterFormFields form={form} onField={onField} errors={formErrors} />
        </FormModal>
      )}
    </>
  );
}
