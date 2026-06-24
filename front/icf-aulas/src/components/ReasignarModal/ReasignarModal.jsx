import { useState, useEffect } from 'react';
import { X, Info, Users, Clock, ChevronDown, Plus } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import Modal from '../Modal/Modal';
import { useReservation } from '../../context/ReservationContext';
import { typeLabel } from '../../schemas/classroom';
import { getTimeSlots } from '../../api/timeslots';
import { labelsToTimeSlotIds } from '../../utils/reservations';
import '../ReservaModal/ReservaModal.css';
import './ReasignarModal.css';

/** @param {number} h @param {number} m @returns {number} */
const toMins = (h, m) => h * 60 + m;

/** @param {number} h @param {number} m @returns {string} */
const fmt = (h, m) => `${h}:${String(m).padStart(2, '0')}`;

/** Returns all possible start time slots (07:00 – 19:30 in 30-min increments). */
function getAllStartSlots() {
  const slots = [];
  for (let h = 7; h <= 19; h++) {
    for (let m = 0; m < 60; m += 30) {
      if (h === 19 && m === 30) continue;
      slots.push({ h, m, label: fmt(h, m) });
    }
  }
  return slots;
}

/** Returns available end time slots given a start time. */
function getEndSlots(startH, startM) {
  const startMins = toMins(startH, startM);
  const slots = [];
  for (let h = 7; h <= 20; h++) {
    for (let m = 0; m < 60; m += 30) {
      if (h === 20 && m > 0) break;
      if (toMins(h, m) <= startMins) continue;
      slots.push({ h, m, label: fmt(h, m) });
    }
  }
  return slots;
}

/**
 * Formats a time-slot's startTime ("HH:MM:SS") as a label ("H:MM").
 * Used to initialise the dropdowns from the reservation's current time slots.
 */
function timeToLabel(hhmmss) {
  if (!hhmmss) return '';
  const [h, m] = hhmmss.split(':').map(Number);
  return fmt(h, m);
}

const START_SLOTS = getAllStartSlots();

// ── Component ─────────────────────────────────────────────────────────────────

/**
 * Modal for rescheduling an existing reservation — allows changing the room
 * and/or time block without touching the class name or recurrence settings.
 * Calls `PATCH /api/v1/reservations/{uuid}/reassign`. ADMIN only.
 *
 * @param {{
 *   open:        boolean,
 *   onClose:     () => void,
 *   reservation: object | null,
 * }} props
 */
export default function ReasignarModal({ open, onClose, reservation }) {
  const { rooms, visibleRooms, reassignMutation } = useReservation();
  const availableRooms = rooms.filter(r => visibleRooms.has(r.uuid));

  // Time-slot catalog (needed to convert labels → IDs for the API)
  const { data: timeslotCatalog = [] } = useQuery({
    queryKey: ['timeslots'],
    queryFn:  getTimeSlots,
    staleTime: Infinity,
  });

  const [roomId,     setRoomId]     = useState('');
  const [startLabel, setStartLabel] = useState('');
  const [endLabel,   setEndLabel]   = useState('');

  useEffect(() => {
    if (!open || !reservation) return;
    // Pre-fill with current reservation values
    setRoomId(reservation.classroomUuid ?? '');
    // Derive labels from the first and last time slot in the ordered list
    const slots = reservation.timeSlots ?? [];
    setStartLabel(slots.length > 0 ? timeToLabel(slots[0].startTime)       : '');
    setEndLabel(slots.length > 0   ? timeToLabel(slots[slots.length - 1].endTime) : '');
  }, [open, reservation]);

  const startSlot = START_SLOTS.find(s => s.label === startLabel) ?? null;
  const endSlots  = startSlot ? getEndSlots(startSlot.h, startSlot.m) : [];
  const room      = rooms.find(r => r.uuid === roomId) ?? null;

  const handleStartChange = val => {
    setStartLabel(val);
    const slot = START_SLOTS.find(s => s.label === val);
    if (!slot) return;
    const eSlots = getEndSlots(slot.h, slot.m);
    if (!eSlots.find(s => s.label === endLabel)) setEndLabel(eSlots[0]?.label ?? '');
  };

  const canSubmit =
    Boolean(roomId) &&
    Boolean(startLabel) &&
    Boolean(endLabel) &&
    !reassignMutation.isPending;

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!canSubmit || !reservation) return;

    const newTimeSlotIds = labelsToTimeSlotIds(timeslotCatalog, startLabel, endLabel);

    // Detect what actually changed to avoid sending unchanged fields
    const currentClassroom = reservation.classroomUuid;
    const currentSlots     = (reservation.timeSlots ?? []).map(s => s.id).join(',');
    const newSlots         = newTimeSlotIds.join(',');

    const payload = {
      uuid: reservation.uuid,
      ...(roomId !== currentClassroom ? { newClassroomUuid: roomId } : {}),
      ...(newSlots !== currentSlots   ? { newTimeSlotIds } : {}),
    };

    // Prevent a no-op call (nothing changed)
    if (!payload.newClassroomUuid && !payload.newTimeSlotIds) {
      onClose();
      return;
    }

    try {
      await reassignMutation.mutateAsync(payload);
      onClose();
    } catch (_) {
      // toast already shown by useApiMutation's onError handler
    }
  };

  return (
    <Modal open={open} className="reasignar-modal">
      <div className="reserva-modal__inner">

        <header className="reserva-modal__header">
          <h2 className="reserva-modal__title">Reasignar Aula</h2>
          <button
            type="button"
            className="reserva-modal__close"
            onClick={onClose}
            aria-label="Cerrar"
          >
            <X size={20} strokeWidth={2.5} />
          </button>
        </header>

        <form onSubmit={handleSubmit} className="reserva-modal__body" noValidate>

          {/* Room selector */}
          <div className="reserva-modal__field">
            <label className="reserva-modal__label">Seleccionar Sala*</label>
            <div className="reserva-modal__select-wrap">
              <select
                className="reserva-modal__select"
                value={roomId}
                onChange={e => setRoomId(e.target.value)}
                required
              >
                <option value="" />
                {availableRooms.map(r => (
                  <option key={r.uuid} value={r.uuid}>{r.label}</option>
                ))}
              </select>
              <ChevronDown size={18} className="reserva-modal__chevron" />
            </div>
          </div>

          {/* Room info card */}
          {room && (
            <div className="reserva-modal__sala-card">
              <div className="reserva-modal__sala-card-header">
                <Info size={16} />
                <span>Información del aula: {room.label}</span>
              </div>
              <div className="reserva-modal__sala-card-details">
                <span><Users size={15} /> Capacidad: {room.capacity} personas</span>
                <span>Tipo: {typeLabel(room.type)}</span>
              </div>
            </div>
          )}

          {/* Time selectors */}
          <div className="reserva-modal__row">
            <div className="reserva-modal__field">
              <label className="reserva-modal__label">Hora de Inicio*</label>
              <div className="reserva-modal__select-wrap reserva-modal__select-wrap--time">
                <Clock size={16} className="reserva-modal__time-icon" />
                <select
                  className="reserva-modal__select reserva-modal__select--time"
                  value={startLabel}
                  onChange={e => handleStartChange(e.target.value)}
                  required
                >
                  {START_SLOTS.map(s => (
                    <option key={s.label} value={s.label}>{s.label}</option>
                  ))}
                </select>
                <ChevronDown size={18} className="reserva-modal__chevron" />
              </div>
            </div>

            <div className="reserva-modal__field">
              <label className="reserva-modal__label">Hora de Fin*</label>
              <div className="reserva-modal__select-wrap reserva-modal__select-wrap--time">
                <Clock size={16} className="reserva-modal__time-icon" />
                <select
                  className="reserva-modal__select reserva-modal__select--time"
                  value={endLabel}
                  onChange={e => setEndLabel(e.target.value)}
                  required
                  disabled={!startLabel || endSlots.length === 0}
                >
                  {endSlots.map(s => (
                    <option key={s.label} value={s.label}>{s.label}</option>
                  ))}
                </select>
                <ChevronDown size={18} className="reserva-modal__chevron" />
              </div>
            </div>
          </div>

          <footer className="reserva-modal__footer">
            <button
              type="button"
              className="reserva-modal__btn reserva-modal__btn--cancel"
              onClick={onClose}
            >
              Cancelar
            </button>
            <button
              type="submit"
              className="reserva-modal__btn reserva-modal__btn--submit"
              disabled={!canSubmit}
            >
              <Plus size={20} />
              {reassignMutation.isPending ? 'Reasignando…' : 'Reasignar Aula'}
            </button>
          </footer>
        </form>
      </div>
    </Modal>
  );
}
