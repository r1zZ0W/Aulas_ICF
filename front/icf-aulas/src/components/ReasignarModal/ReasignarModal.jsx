import { useEffect } from 'react';
import { X, Info, Users, Clock, ChevronDown, Plus } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import Modal from '../Modal/Modal';
import { useReservation } from '../../context/ReservationContext';
import { typeLabel } from '../../schemas/classroom';
import { getTimeSlots } from '../../api/timeslots';
import { labelsToTimeSlotIds } from '../../utils/reservations';
import { useZodForm } from '../../hooks/useZodForm';
import { ReasignFormSchema, REASSIGN_DTO_MAP } from '../../schemas/reservation/reasignForm.js';
import '../ReservaModal/ReservaModal.css';
import './ReasignarModal.css';
import { TriangleAlert } from 'lucide-react';

const EMPTY_REASIGN = { roomId: '', startLabel: '', endLabel: '' };

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
    queryFn: getTimeSlots,
    staleTime: Infinity,
  });

  // Zod-backed form instance — single source of truth for roomId/startLabel/endLabel.
  const zod = useZodForm(EMPTY_REASIGN, ReasignFormSchema, { dtoMap: REASSIGN_DTO_MAP });
  const { roomId, startLabel, endLabel } = zod.formData;

  useEffect(() => {
    if (!open || !reservation) return;
    // Pre-fill with current reservation values
    const slots = reservation.timeSlots ?? [];
    zod.reset({
      roomId: reservation.classroomUuid ?? '',
      startLabel: slots.length > 0 ? timeToLabel(slots[0].startTime) : '',
      endLabel: slots.length > 0 ? timeToLabel(slots[slots.length - 1].endTime) : '',
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, reservation]);

  const startSlot = START_SLOTS.find(s => s.label === startLabel) ?? null;
  const endSlots = startSlot ? getEndSlots(startSlot.h, startSlot.m) : [];
  const room = rooms.find(r => r.uuid === roomId) ?? null;

  const handleRoomChange = val => zod.handleChange('roomId', val);

  const handleStartChange = val => {
    zod.handleChange('startLabel', val);
    const slot = START_SLOTS.find(s => s.label === val);
    if (!slot) return;
    const eSlots = getEndSlots(slot.h, slot.m);
    if (!eSlots.find(s => s.label === endLabel)) zod.handleChange('endLabel', eSlots[0]?.label ?? '');
  };

  // Readiness of async infrastructure only — input validity is validateAll()'s job below.
  const isReadyToSubmit = !reassignMutation.isPending;

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!reservation) return;

    const isValid = zod.validateAll();
    if (!isValid) return;

    const newTimeSlotIds = labelsToTimeSlotIds(timeslotCatalog, startLabel, endLabel);

    // Detect what actually changed to avoid sending unchanged fields
    const currentClassroom = reservation.classroomUuid;
    const currentSlots = (reservation.timeSlots ?? []).map(s => s.id).join(',');
    const newSlots = newTimeSlotIds.join(',');

    const payload = {
      uuid: reservation.uuid,
      ...(roomId !== currentClassroom ? { newClassroomUuid: roomId } : {}),
      ...(newSlots !== currentSlots ? { newTimeSlotIds } : {}),
    };

    // Prevent a no-op call (nothing changed)
    if (!payload.newClassroomUuid && !payload.newTimeSlotIds) {
      onClose();
      return;
    }

    try {
      await reassignMutation.mutateAsync(payload);
      onClose();
    } catch (err) {
      // toast already shown by useApiMutation's onError handler; additionally highlight
      // the offending input(s) — e.g. a slot conflict marks both startLabel and endLabel.
      if (err.fieldErrors) zod.setServerErrors(err.fieldErrors);
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
            <label className="reserva-modal__label">
              Seleccionar Sala
              {/* Cosmético — Zod es la única fuente de verdad */}
              <span className="reserva-modal__required" aria-hidden="true">*</span>
            </label>
            <div className={`reserva-modal__select-wrap${zod.errors.roomId ? ' reserva-modal__select-wrap--error' : ''}`}>
              <select
                className="reserva-modal__select"
                value={roomId}
                onChange={e => handleRoomChange(e.target.value)}
                onBlur={() => zod.handleBlur('roomId')}
              >
                <option value="" />
                {availableRooms.map(r => (
                  <option key={r.uuid} value={r.uuid}>{r.label}</option>
                ))}
              </select>
              <ChevronDown size={18} className="reserva-modal__chevron" />
            </div>
            {zod.errors.roomId && <span className="reserva-modal__error">{zod.errors.roomId}</span>}
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

          {/* Important info card */}
          <div className="reserva-modal__sala-card reserva-modal__sala-card--danger">
            <div className="reserva-modal__sala-card-header">
              <TriangleAlert size={16} />
              <span>Información importante</span>
            </div>
            <div className="reserva-modal__sala-card-details">
              <span>
                Se le notificará por correo electrónico al maestro al que se le reasigne la reserva
                y a todos <br /> los administradores del sistema.
              </span>
            </div>
          </div>

          {/* Time selectors */}
          <div className="reserva-modal__row">
            <div className="reserva-modal__field">
              <label className="reserva-modal__label">
                Hora de Inicio
                {/* Cosmético — Zod es la única fuente de verdad */}
                <span className="reserva-modal__required" aria-hidden="true">*</span>
              </label>
              <div className={`reserva-modal__select-wrap reserva-modal__select-wrap--time${zod.errors.startLabel ? ' reserva-modal__select-wrap--error' : ''}`}>
                <Clock size={16} className="reserva-modal__time-icon" />
                <select
                  className="reserva-modal__select reserva-modal__select--time"
                  value={startLabel}
                  onChange={e => handleStartChange(e.target.value)}
                  onBlur={() => zod.handleBlur('startLabel')}
                >
                  {START_SLOTS.map(s => (
                    <option key={s.label} value={s.label}>{s.label}</option>
                  ))}
                </select>
                <ChevronDown size={18} className="reserva-modal__chevron" />
              </div>
              {zod.errors.startLabel && <span className="reserva-modal__error">{zod.errors.startLabel}</span>}
            </div>

            <div className="reserva-modal__field">
              <label className="reserva-modal__label">
                Hora de Fin
                {/* Cosmético — Zod es la única fuente de verdad */}
                <span className="reserva-modal__required" aria-hidden="true">*</span>
              </label>
              <div className={`reserva-modal__select-wrap reserva-modal__select-wrap--time${zod.errors.endLabel ? ' reserva-modal__select-wrap--error' : ''}`}>
                <Clock size={16} className="reserva-modal__time-icon" />
                <select
                  className="reserva-modal__select reserva-modal__select--time"
                  value={endLabel}
                  onChange={e => zod.handleChange('endLabel', e.target.value)}
                  onBlur={() => zod.handleBlur('endLabel')}
                  disabled={!startLabel || endSlots.length === 0}
                >
                  {endSlots.map(s => (
                    <option key={s.label} value={s.label}>{s.label}</option>
                  ))}
                </select>
                <ChevronDown size={18} className="reserva-modal__chevron" />
              </div>
              {zod.errors.endLabel && <span className="reserva-modal__error">{zod.errors.endLabel}</span>}
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
              disabled={!isReadyToSubmit}
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
