import { useState, useEffect } from 'react';
import { X, Info, Users, Clock, ChevronDown, Plus, ArrowLeft, Calendar, Repeat } from 'lucide-react';
import Modal from '../Modal/Modal';
import { useReservation } from '../../context/ReservationContext';
import { typeLabel } from '../../schemas/classroom';
import './ReservaModal.css';

const MONTHS_ES = [
  'Enero','Febrero','Marzo','Abril','Mayo','Junio',
  'Julio','Agosto','Septiembre','Octubre','Noviembre','Diciembre',
];
const WEEKDAYS_SHORT = ['Dom','Lun','Mar','Mié','Jue','Vie','Sáb'];

/**
 * @param {Date} a
 * @param {Date} b
 * @returns {boolean}
 */
function sameDay(a, b) {
  return (
    a.getFullYear() === b.getFullYear() &&
    a.getMonth()    === b.getMonth()    &&
    a.getDate()     === b.getDate()
  );
}

/** @param {number} h @param {number} m @returns {number} */
function toMins(h, m) { return h * 60 + m; }

/** @param {number} h @param {number} m @returns {string} */
function fmt(h, m) { return `${h}:${String(m).padStart(2, '0')}`; }

/**
 * Returns available start time slots for the given date.
 * Slots before the current half-hour are excluded when the date is today.
 *
 * @param {Date} date
 * @returns {Array<{ h: number, m: number, label: string }>}
 */
function getStartSlots(date) {
  const now = new Date();
  const isToday = sameDay(date, now);
  const nowMins = isToday
    ? now.getHours() * 60 + (now.getMinutes() >= 30 ? 30 : 0)
    : -1;
  const slots = [];
  for (let h = 7; h <= 19; h++) {
    for (let m = 0; m < 60; m += 30) {
      if (h === 19 && m === 30) continue;
      if (toMins(h, m) <= nowMins) continue;
      slots.push({ h, m, label: fmt(h, m) });
    }
  }
  return slots;
}

/**
 * Returns available end time slots given a start time.
 *
 * @param {number} startH
 * @param {number} startM
 * @returns {Array<{ h: number, m: number, label: string }>}
 */
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
 * Finds the slot whose time matches the given Date (snapped to half-hour),
 * or falls back to the first available slot.
 *
 * @param {Array<{ h: number, m: number, label: string }>} slots
 * @param {Date|null} date
 * @returns {{ h: number, m: number, label: string } | null}
 */
function snapSlot(slots, date) {
  if (!date || !slots.length) return slots[0] ?? null;
  const d = new Date(date);
  const m = d.getMinutes() >= 30 ? 30 : 0;
  return slots.find(s => s.h === d.getHours() && s.m === m) ?? slots[0];
}

// ── DatePicker sub-component ──────────────────────────────────────────────────

/**
 * Inline month-grid date picker.
 *
 * @param {{ selectedDate: Date|null, onSelect: (date: Date) => void }} props
 */
function DatePicker({ selectedDate, onSelect }) {
  const today = new Date();
  const [viewYear, setViewYear]   = useState(today.getFullYear());
  const [viewMonth, setViewMonth] = useState(today.getMonth());

  const daysInMonth    = new Date(viewYear, viewMonth + 1, 0).getDate();
  const firstDay       = new Date(viewYear, viewMonth, 1).getDay();
  const todayMidnight  = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  const isCurrentMonth = viewYear === today.getFullYear() && viewMonth === today.getMonth();

  const prevMonth = () => {
    if (isCurrentMonth) return;
    if (viewMonth === 0) { setViewYear(y => y - 1); setViewMonth(11); }
    else setViewMonth(m => m - 1);
  };
  const nextMonth = () => {
    if (viewMonth === 11) { setViewYear(y => y + 1); setViewMonth(0); }
    else setViewMonth(m => m + 1);
  };

  const isPast     = d => new Date(viewYear, viewMonth, d) < todayMidnight;
  const isToday    = d => isCurrentMonth && d === today.getDate();
  const isSelected = d =>
    selectedDate &&
    selectedDate.getFullYear() === viewYear &&
    selectedDate.getMonth()    === viewMonth &&
    selectedDate.getDate()     === d;

  const cells = [];
  for (let i = 0; i < firstDay; i++) cells.push(null);
  for (let d = 1; d <= daysInMonth; d++) cells.push(d);

  return (
    <div className="date-picker">
      <p className="date-picker__prompt">Elige la fecha para tu reserva</p>

      <div className="date-picker__nav">
        <button
          type="button"
          className="date-picker__nav-btn"
          onClick={prevMonth}
          disabled={isCurrentMonth}
          aria-label="Mes anterior"
        >
          <i className="bi bi-chevron-left" />
        </button>
        <span className="date-picker__month">{MONTHS_ES[viewMonth]} {viewYear}</span>
        <button
          type="button"
          className="date-picker__nav-btn"
          onClick={nextMonth}
          aria-label="Siguiente mes"
        >
          <i className="bi bi-chevron-right" />
        </button>
      </div>

      <div className="date-picker__grid">
        {WEEKDAYS_SHORT.map(wd => (
          <span key={wd} className="date-picker__weekday">{wd}</span>
        ))}
        {cells.map((d, i) => (
          <button
            key={i}
            type="button"
            disabled={!d || isPast(d)}
            onClick={() => d && !isPast(d) && onSelect(new Date(viewYear, viewMonth, d))}
            className={[
              'date-picker__cell',
              !d                                 ? 'date-picker__cell--empty'    : '',
              d && isPast(d)                     ? 'date-picker__cell--past'     : '',
              d && isToday(d) && !isSelected(d)  ? 'date-picker__cell--today'    : '',
              d && isSelected(d)                 ? 'date-picker__cell--selected' : '',
            ].filter(Boolean).join(' ')}
          >
            {d || ''}
          </button>
        ))}
      </div>
    </div>
  );
}

// ── Main component ────────────────────────────────────────────────────────────

/**
 * Two-step modal for creating a new reservation.
 * Step 1: date picker. Step 2: room + time + class name form.
 *
 * @param {{
 *   open:         boolean,
 *   onClose:      () => void,
 *   initialStart: Date | null,
 *   initialEnd:   Date | null,
 * }} props
 */
export default function ReservaModal({ open, onClose, initialStart = null, initialEnd = null }) {
  const { rooms, visibleRooms, addReservation } = useReservation();
  const availableRooms = rooms.filter(r => visibleRooms.has(r.uuid));

  const [step,       setStep]       = useState(1);
  const [pickedDate, setPickedDate] = useState(null);
  const [roomId,     setRoomId]     = useState('');
  const [className,  setClassName]  = useState('');
  const [startLabel, setStartLabel] = useState('');
  const [endLabel,   setEndLabel]   = useState('');
  const [attendees,  setAttendees]  = useState('');
  const [recurring,  setRecurring]  = useState(false);

  // Reset room selection when the room gets hidden from the sidebar
  useEffect(() => {
    if (roomId && !visibleRooms.has(roomId)) {
      setRoomId('');
      setAttendees('');
    }
  }, [visibleRooms, roomId]);

  useEffect(() => {
    if (!open) return;

    setRoomId('');
    setClassName('');
    setAttendees('');
    setRecurring(false);

    if (initialStart) {
      const d          = new Date(initialStart);
      const startSlots = getStartSlots(d);
      const defStart   = snapSlot(startSlots, initialStart);
      const endSlots   = defStart ? getEndSlots(defStart.h, defStart.m) : [];
      const defEnd     = snapSlot(endSlots, initialEnd);
      setPickedDate(d);
      setStep(2);
      setStartLabel(defStart?.label ?? '');
      setEndLabel(defEnd?.label   ?? '');
    } else {
      setStep(1);
      setPickedDate(null);
      setStartLabel('');
      setEndLabel('');
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const handleDatePick = (date) => {
    setPickedDate(date);
    const startSlots = getStartSlots(date);
    const defStart   = startSlots[0] ?? null;
    const endSlots   = defStart ? getEndSlots(defStart.h, defStart.m) : [];
    const defEnd     = endSlots[0] ?? null;
    setStartLabel(defStart?.label ?? '');
    setEndLabel(defEnd?.label   ?? '');
    setStep(2);
  };

  const forDate    = pickedDate ?? new Date();
  const startSlots = getStartSlots(forDate);
  const startSlot  = startSlots.find(s => s.label === startLabel) ?? null;
  const endSlots   = startSlot ? getEndSlots(startSlot.h, startSlot.m) : [];
  const room       = rooms.find(r => r.uuid === roomId) ?? null;

  const handleRoomChange  = val => { setRoomId(val); setAttendees(''); };
  const handleStartChange = val => {
    setStartLabel(val);
    const slot = startSlots.find(s => s.label === val);
    if (!slot) return;
    const eSlots = getEndSlots(slot.h, slot.m);
    if (!eSlots.find(s => s.label === endLabel)) setEndLabel(eSlots[0]?.label ?? '');
  };

  const canSubmit =
    Boolean(roomId) &&
    className.trim().length > 0 &&
    Boolean(startLabel) &&
    Boolean(endLabel) &&
    Number(attendees) >= 1;

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!canSubmit) return;
    const [sh, sm] = startLabel.split(':').map(Number);
    const [eh, em] = endLabel.split(':').map(Number);
    const newStart = new Date(forDate.getFullYear(), forDate.getMonth(), forDate.getDate(), sh, sm, 0);
    const newEnd   = new Date(forDate.getFullYear(), forDate.getMonth(), forDate.getDate(), eh, em, 0);

    const reservationsToCreate = [];

    if (recurring) {
      const currentStart = new Date(newStart);
      const currentEnd   = new Date(newEnd);
      const limitDate    = new Date(newStart.getFullYear(), 7, 31, 23, 59, 59);

      while (currentStart <= limitDate) {
        reservationsToCreate.push({
          roomId,
          title:      className,
          start:      new Date(currentStart),
          end:        new Date(currentEnd),
          attendees:  Number(attendees),
          recurrente: true,
        });
        currentStart.setDate(currentStart.getDate() + 7);
        currentEnd.setDate(currentEnd.getDate() + 7);
      }
    }

    if (reservationsToCreate.length === 0) {
      reservationsToCreate.push({
        roomId,
        title:      className,
        start:      newStart,
        end:        newEnd,
        attendees:  Number(attendees),
        recurrente: recurring,
      });
    }

    addReservation(reservationsToCreate);
    onClose();
  };

  const maxAttendees = room?.capacity ?? 0;

  const formatDate = d =>
    d.toLocaleDateString('es-MX', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });

  return (
    <Modal open={open} className="reserva-modal">
      <div className="reserva-modal__inner">

        {/* ── Header ─────────────────────────────────────── */}
        <header className="reserva-modal__header">
          <div className="reserva-modal__header-left">
            {step === 2 && !initialStart && (
              <button
                type="button"
                className="reserva-modal__back"
                onClick={() => setStep(1)}
                aria-label="Volver a seleccionar fecha"
              >
                <ArrowLeft size={18} strokeWidth={2.5} />
              </button>
            )}
            <h2 className="reserva-modal__title">
              {step === 1 ? 'Seleccionar Fecha' : 'Asignar Aula'}
            </h2>
          </div>
          <button
            type="button"
            className="reserva-modal__close"
            onClick={onClose}
            aria-label="Cerrar"
          >
            <X size={20} strokeWidth={2.5} />
          </button>
        </header>

        {/* ── Step 1: Date picker ─────────────────────────── */}
        {step === 1 && (
          <DatePicker selectedDate={pickedDate} onSelect={handleDatePick} />
        )}

        {/* ── Step 2: Reservation form ────────────────────── */}
        {step === 2 && (
          <form onSubmit={handleSubmit} className="reserva-modal__body" noValidate>

            {/* Selected date badge */}
            {pickedDate && (
              <div className="reserva-modal__date-badge">
                <Calendar size={15} />
                <span>{formatDate(pickedDate)}</span>
              </div>
            )}

            {/* Room selector */}
            <div className="reserva-modal__field">
              <label className="reserva-modal__label">Seleccionar Sala*</label>
              <div className="reserva-modal__select-wrap">
                <select
                  className="reserva-modal__select"
                  value={roomId}
                  onChange={e => handleRoomChange(e.target.value)}
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

            {/* Class name */}
            <div className="reserva-modal__field">
              <label className="reserva-modal__label">Nombre de la clase*</label>
              <input
                type="text"
                className="reserva-modal__input"
                placeholder="Ej. Introducción a Mecánica de Fluidos"
                value={className}
                onChange={e => setClassName(e.target.value)}
                required
              />
            </div>

            {/* Start / End time */}
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
                    {startSlots.length === 0 && (
                      <option value="">Sin horarios disponibles</option>
                    )}
                    {startSlots.map(s => (
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

            {/* Attendee count */}
            <div className="reserva-modal__field">
              <label className="reserva-modal__label">Número de alumnos*</label>
              <div className="reserva-modal__select-wrap">
                <select
                  className="reserva-modal__select"
                  value={attendees}
                  onChange={e => setAttendees(e.target.value)}
                  required
                  disabled={!room}
                >
                  <option value="">
                    {room ? '' : 'Selecciona un aula primero'}
                  </option>
                  {Array.from({ length: maxAttendees }, (_, i) => i + 1).map(n => (
                    <option key={n} value={n}>{n}</option>
                  ))}
                </select>
                <ChevronDown size={18} className="reserva-modal__chevron" />
              </div>
            </div>

            {/* Recurring toggle */}
            <div className="reserva-modal__recurrente">
              <div className="reserva-modal__recurrente-info">
                <span className="reserva-modal__recurrente-label">
                  <Repeat size={14} />
                  Recurrente todo el semestre
                </span>
                <span className="reserva-modal__recurrente-desc">
                  Se agendará en el mismo horario cada semana
                </span>
              </div>
              <button
                type="button"
                role="switch"
                aria-checked={recurring}
                className={`reserva-modal__toggle${recurring ? ' reserva-modal__toggle--on' : ''}`}
                onClick={() => setRecurring(r => !r)}
              />
            </div>

            {/* Footer */}
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
                Reservar Aula
              </button>
            </footer>
          </form>
        )}
      </div>
    </Modal>
  );
}
