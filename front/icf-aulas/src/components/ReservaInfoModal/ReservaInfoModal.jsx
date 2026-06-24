import { X, Info, Users, Clock, Pencil, Ban } from 'lucide-react';
import Modal from '../Modal/Modal';
import { useAuth } from '../../context/AuthContext';
import { ROLES } from '../../utils/roles';
import { useReservation } from '../../context/ReservationContext';
import { typeLabel } from '../../schemas/classroom';
import { slotsToRange } from '../../utils/reservations';
import '../ReservaModal/ReservaModal.css';
import './ReservaInfoModal.css';

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
 * Admins see "Editar" (→ reassign) and "Cancelar (admin)" buttons.
 * Teachers see "Cancelar" for their own reservations.
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
  } = useReservation();

  if (!open || !reservation) return null;

  // Resolve room display info from the classroom UUID in the DTO.
  // Falls back to reservation.classroomName when the room isn't in the local catalog.
  const room = roomById[reservation.classroomUuid];
  const roomName = room?.label ?? reservation.classroomName ?? '—';

  // Compute event start/end from the ordered time-slot list
  const { start, end } = slotsToRange(reservation.date, reservation.timeSlots ?? []);

  const handleCancel = () => {
    if (isAdmin) {
      cancelReservationAdminMutation.mutate(reservation.uuid, {
        onSuccess: onClose,
      });
    } else {
      cancelReservationMutation.mutate(reservation.uuid, {
        onSuccess: onClose,
      });
    }
  };

  const isCancelling =
    cancelReservationMutation.isPending || cancelReservationAdminMutation.isPending;

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
              value={reservation.classroomName || '—'}
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

            {/* Cancel reservation */}
            {isAdmin && (
              <button
                type="button"
                className="reserva-modal__btn reserva-modal__btn--danger"
                onClick={handleCancel}
                disabled={isCancelling}
              >
                <Ban size={16} />
                {isCancelling ? 'Cancelando…' : 'Cancelar'}
              </button>
            )}

            {/* Admin reassign */}
            {isAdmin && (
              <button
                type="button"
                className="reserva-modal__btn reserva-modal__btn--submit"
                onClick={onEdit}
              >
                <Pencil size={18} />
                Reasignar
              </button>
            )}
          </footer>
        </div>
      </div>
    </Modal>
  );
}
