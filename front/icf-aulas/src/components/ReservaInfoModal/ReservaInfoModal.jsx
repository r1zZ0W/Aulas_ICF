import { X, Info, Users, Clock, Pencil, Ban } from 'lucide-react';
import Modal from '../Modal/Modal';
import { useAuth } from '../../context/AuthContext';
import { ROLES } from '../../utils/roles';
import { useReservation } from '../../context/ReservationContext';
import { typeLabel } from '../../schemas/classroom';
import { slotsToRange, isReservationPast } from '../../utils/reservations';
import '../ReservaModal/ReservaModal.css';
import './ReservaInfoModal.css';
import { TriangleAlert } from 'lucide-react';

/**
 * Formats a local Date as "H:MM" (no seconds).
 *
 * @param {Date|null} date
 * @returns {string}
 */
function fmtTime(date) {
  if (!date) return '—';
  return `${date.getHours()}:${String(date.getMinutes()).padStart(2, '0')}`;
}

// ── Component ─────────────────────────────────────────────────────────────────

/**
 * Read-only modal displaying the details of a reservation instance.
 *
 * Reads from `ReservInstanceResponseDTO` fields:
 *  - `reservation.motivo` → class/event name
 *  - `reservation.classroomUuid` → room lookup in `roomById`
 *  - `reservation.timeSlots` + `reservation.date` → start/end times
 *  - `reservation.numAsistentes` → attendee count (informational, not shown currently)
 *
 * Visibility rules:
 *  - Admin               → "Ver estudiantes" always (any status, past or future — opens
 *                          {@link ReservaStudentsModal}); "Cancelar" (admin mutation) +
 *                          "Reasignar" only when !isPast && status === 'ACTIVE'.
 *  - Teacher (owner)     → "Cancelar" (user mutation) only when !isPast && status === 'ACTIVE'.
 *  - Teacher (not owner) → read-only, no action buttons.
 *
 * @param {{
 *   open:        boolean,
 *   onClose:     () => void,
 *   reservation: object | null,
 *   onEdit:      () => void,
 * }} props
 */
export default function ReservaInfoModal({ open, onClose, reservation, onEdit }) {
  const { user } = useAuth();
  const isAdmin = user?.role === ROLES.ADMIN;

  const {
    roomById,
    cancelReservationMutation,
    cancelReservationAdminMutation,
    openStudentsModal,
  } = useReservation();

  if (!open || !reservation) return null;

  // Resolve room display info from the classroom UUID in the DTO.
  // Falls back to reservation.classroomName when the room isn't in the local catalog.
  const room = roomById[reservation.classroomUuid];
  const roomName = room?.label ?? reservation.classroomName ?? '—';

  // Compute event start/end from the ordered time-slot list
  const { start, end } = slotsToRange(reservation.date, reservation.timeSlots ?? []);

  // True when the logged-in teacher is the owner of this reservation.
  // Admins bypass this check — they can act on any reservation.
  const isOwner = user?.uuid === reservation.userUuid;

  const handleCancelAdmin = () =>
    cancelReservationAdminMutation.mutate(reservation.uuid, { onSuccess: onClose });

  const handleCancelOwn = () =>
    cancelReservationMutation.mutate(reservation.uuid, { onSuccess: onClose });

  const isCancelling =
    cancelReservationMutation.isPending || cancelReservationAdminMutation.isPending;

  // Hide cancel/reassign actions when the reservation is already in the past
  const isPast = isReservationPast(reservation);

  // Guard: show action buttons only for active, future reservations
  const canAct = !isPast && reservation.status === 'ACTIVE';

  return (
    <Modal open={open} className="reserva-info-modal">
      <div className="reserva-modal__inner">

        <header className="reserva-modal__header">
          <h2 className="reserva-modal__title">Información de la reserva</h2>
          <button
            type="button"
            className="reserva-modal__close"
            onClick={onClose}
            aria-label="Cerrar"
          >
            <X size={20} strokeWidth={2.5} />
          </button>
        </header>

        <div className="reserva-modal__body">

          {/* Class name (read-only) */}
          <div className="reserva-modal__field">
            <label className="reserva-modal__label">Nombre de la clase</label>
            <input
              type="text"
              className="reserva-modal__input reserva-info-modal__readonly"
              value={reservation.title ?? reservation.classroomName ?? '—'}
              readOnly
            />
          </div>

          {/* Maestro (read-only) */}
          <div className="reserva-modal__field">
            <label className="reserva-modal__label">Maestro</label>
            <input
              type="text"
              className="reserva-modal__input reserva-info-modal__readonly"
              value={reservation.userFullName || '—'}
              readOnly
            />
          </div>

          {/* Sección de Asistentes / Estudiantes dentro del body */}
          <div className="reserva-modal__field">
            <div className="reserva-info-modal__students-header">
              <label className="reserva-modal__label">Estudiantes inscritos</label>
              {isAdmin && (
                <button
                  type="button"
                  className="reserva-info-modal__inline-btn"
                  onClick={openStudentsModal}
                >
                  <Users size={15} />
                  Ver lista completa
                </button>
              )}
            </div>
            <input
              type="text"
              className="reserva-modal__input reserva-info-modal__readonly"
              value={`${reservation.numAsistentes ?? 0} estudiantes registrados`}
              readOnly
            />
          </div>

          {/* Room info card */}
          <div className="reserva-modal__sala-card">
            <div className="reserva-modal__sala-card-header">
              <Info size={16} />
              <span>Información del aula: {roomName}</span>
            </div>
            {room && (
              <div className="reserva-modal__sala-card-details">
                <span><Users size={15} /> Capacidad: {room.capacity} personas</span>
                <span>Tipo: {typeLabel(room.type)}</span>
              </div>
            )}
          </div>

          {/* Time display (read-only) */}
          <div className="reserva-modal__row">
            <div className="reserva-modal__field">
              <label className="reserva-modal__label">Hora de Inicio</label>
              <div className="reserva-modal__select-wrap reserva-modal__select-wrap--time">
                <Clock size={16} className="reserva-modal__time-icon" />
                <input
                  type="text"
                  className="reserva-modal__select reserva-modal__select--time reserva-info-modal__readonly"
                  value={fmtTime(start)}
                  readOnly
                />
              </div>
            </div>
            <div className="reserva-modal__field">
              <label className="reserva-modal__label">Hora de Fin</label>
              <div className="reserva-modal__select-wrap reserva-modal__select-wrap--time">
                <Clock size={16} className="reserva-modal__time-icon" />
                <input
                  type="text"
                  className="reserva-modal__select reserva-modal__select--time reserva-info-modal__readonly"
                  value={fmtTime(end)}
                  readOnly
                />
              </div>
            </div>
          </div>

          <footer className="reserva-modal__footer">
            <button
              type="button"
              className="reserva-modal__btn reserva-modal__btn--cancel"
              onClick={onClose}
            >
              Cerrar
            </button>

            {/* ── Admin actions ─────────────────────────────────────────
                 Visible only to admins on active, future reservations. */}
            {isAdmin && canAct && (
              <>
                <button
                  type="button"
                  className="reserva-modal__btn reserva-modal__btn--danger"
                  onClick={handleCancelAdmin}
                  disabled={isCancelling}
                >
                  <Ban size={16} />
                  {isCancelling ? 'Cancelando…' : 'Cancelar'}
                </button>
                <button
                  type="button"
                  className="reserva-modal__btn reserva-modal__btn--submit"
                  onClick={onEdit}
                >
                  <Pencil size={18} />
                  Reasignar
                </button>
              </>
            )}

            {/* ── Teacher owner action ──────────────────────────────────
                 Only the owner teacher can cancel their own reservation.
                 A teacher viewing someone else's reservation gets no buttons. */}
            {!isAdmin && isOwner && canAct && (
              <button
                type="button"
                className="reserva-modal__btn reserva-modal__btn--danger"
                onClick={handleCancelOwn}
                disabled={isCancelling}
              >
                <Ban size={16} />
                {isCancelling ? 'Cancelando…' : 'Cancelar'}
              </button>
            )}
          </footer>
        </div>
      </div>
    </Modal>
  );
}
