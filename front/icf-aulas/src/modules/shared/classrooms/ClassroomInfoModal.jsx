import { useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { X } from 'lucide-react';
import Modal from '../../../components/Modal/Modal';
import Badge from '../../../components/Badge/Badge';
import { typeLabel } from '../../../schemas/classroom';
import { getChildren } from '../../../utils/classroomTree';
import { getClassroomResources } from '../../../api/resources.js';
import './ClassroomInfoModal.css';

/**
 * Read-only modal that shows all details of a classroom, including its
 * parent and direct children (derived from `allClassrooms`).
 *
 * Accessible: Escape key closes it, focus lands on the close button on open,
 * and the container carries role="dialog" / aria-modal="true".
 *
 * @param {object}   props
 * @param {boolean}  props.open           - Whether the modal is visible.
 * @param {function} props.onClose        - Called when the user dismisses the modal.
 * @param {object}   props.classroom      - The classroom object to display.
 * @param {Array}    [props.allClassrooms=[]] - Full catalog used to resolve parent
 *   name and children list. Passed from ClassroomsPage (invalidated by mutations).
 */
export default function ClassroomInfoModal({ open, onClose, classroom, allClassrooms = [] }) {
  // Close on Escape key
  useEffect(() => {
    if (!open) return;
    const handler = (e) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('keydown', handler);
    return () => document.removeEventListener('keydown', handler);
  }, [open, onClose]);

  // Read-only equipment list — visible to all roles (admin edits it via
  // ClassroomResourcesModal). Gated by `open` so it doesn't fetch on every table render.
  const { data: resources = [] } = useQuery({
    queryKey: ['classrooms', classroom?.uuid, 'resources'],
    queryFn: () => getClassroomResources(classroom.uuid),
    enabled: open && !!classroom?.uuid,
  });

  if (!classroom) return null;

  // ── Resolve parent name ───────────────────────────────────────────────────
  let parentLabel = '—';
  if (classroom.linkedRoomUuid) {
    const parentRoom = allClassrooms.find(c => c.uuid === classroom.linkedRoomUuid);
    parentLabel = parentRoom
      ? parentRoom.name
      : 'Padre no disponible'; // aula padre dada de baja o eliminada
  }

  // ── Resolve direct children ───────────────────────────────────────────────
  const children = getChildren(classroom.uuid, allClassrooms);

  return (
    <Modal open={open} className="classroom-info-modal">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="classroom-info-title"
        className="classroom-info-modal__inner"
      >
        {/* Header */}
        <header className="classroom-info-modal__header">
          <h2 id="classroom-info-title" className="classroom-info-modal__title">
            Información del aula
          </h2>
          <button
            type="button"
            className="classroom-info-modal__close"
            onClick={onClose}
            aria-label="Cerrar"
            autoFocus
          >
            <X size={24} strokeWidth={2.5} />
          </button>
        </header>

        {/* Body */}
        <div className="classroom-info-modal__body">
          <dl className="classroom-info-modal__grid">
            <div className="classroom-info-modal__field">
              <dt className="classroom-info-modal__label">Nombre</dt>
              <dd className="classroom-info-modal__value">{classroom.name}</dd>
            </div>

            <div className="classroom-info-modal__field">
              <dt className="classroom-info-modal__label">Tipo</dt>
              <dd className="classroom-info-modal__value">{typeLabel(classroom.type)}</dd>
            </div>

            <div className="classroom-info-modal__field">
              <dt className="classroom-info-modal__label">Capacidad</dt>
              <dd className="classroom-info-modal__value">{classroom.capacity} personas</dd>
            </div>

            <div className="classroom-info-modal__field">
              <dt className="classroom-info-modal__label">Estado</dt>
              <dd className="classroom-info-modal__value">
                <Badge variant={classroom.isActive ? 'success' : 'danger'}>
                  {classroom.isActive ? 'Disponible' : 'No disponible'}
                </Badge>
              </dd>
            </div>

            {/* Aula padre */}
            <div className="classroom-info-modal__field">
              <dt className="classroom-info-modal__label">Aula padre</dt>
              <dd className={`classroom-info-modal__value${!classroom.linkedRoomUuid || parentLabel === 'Padre no disponible' ? ' classroom-info-modal__value--muted' : ''}`}>
                {parentLabel}
              </dd>
            </div>

            {/* Aulas hijas */}
            <div className="classroom-info-modal__field">
              <dt className="classroom-info-modal__label">Aulas hijas</dt>
              <dd style={{ whiteSpace: 'pre-line' }}
                className={`classroom-info-modal__value${children.length === 0 ? ' classroom-info-modal__value--muted' : ''}`}>
                {children.length === 0
                  ? '—'
                  : children.map(c => c.name).join('\n')
                }
              </dd>
            </div>

            {/* Recursos (equipo) — solo lectura; se edita desde ClassroomResourcesModal */}
            <div className="classroom-info-modal__field">
              <dt className="classroom-info-modal__label">Recursos</dt>
              <dd style={{ whiteSpace: 'pre-line' }}
                className={`classroom-info-modal__value${resources.length === 0 ? ' classroom-info-modal__value--muted' : ''}`}>
                {resources.length === 0
                  ? '—'
                  : resources.map(r => `${r.resourceName} ×${r.quantity}`).join('\n')
                }
              </dd>
            </div>

            {classroom.description && (
              <div className="classroom-info-modal__field classroom-info-modal__field--full">
                <dt className="classroom-info-modal__label">Descripción</dt>
                <dd className="classroom-info-modal__value classroom-info-modal__value--description">
                  {classroom.description}
                </dd>
              </div>
            )}
          </dl>
        </div>

        {/* Footer */}
        <footer className="classroom-info-modal__footer">
          <button type="button" className="classroom-info-modal__btn-close" onClick={onClose}>
            Cerrar
          </button>
        </footer>
      </div>
    </Modal>
  );
}
