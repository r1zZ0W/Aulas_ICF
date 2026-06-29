import { useState } from 'react';
import { X, Info, Users, Clock, ChevronDown, Plus, ArrowLeft, Calendar, Repeat } from 'lucide-react';
import Modal from '../Modal/Modal';
import { typeLabel } from '../../schemas/classroom';
import { useReservaModal, WEEKDAY_OPTIONS } from './useReservaModal';
import './ReservaModal.css';
import { toDateString } from '../../utils/reservations';

const MONTHS_ES = [
  'Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
  'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre',
];
const WEEKDAYS_SHORT = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'];

// ── DatePicker sub-component ──────────────────────────────────────────────────

function DatePicker({ selectedDate, onSelect }) {
  const today = new Date();
  const [viewYear, setViewYear] = useState(today.getFullYear());
  const [viewMonth, setViewMonth] = useState(today.getMonth());

  const daysInMonth = new Date(viewYear, viewMonth + 1, 0).getDate();
  const firstDay = new Date(viewYear, viewMonth, 1).getDay();
  const todayMidnight = new Date(today.getFullYear(), today.getMonth(), today.getDate());
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

  const isPast = d => new Date(viewYear, viewMonth, d) < todayMidnight;
  const isToday = d => isCurrentMonth && d === today.getDate();
  const isSelected = d =>
    selectedDate &&
    selectedDate.getFullYear() === viewYear &&
    selectedDate.getMonth() === viewMonth &&
    selectedDate.getDate() === d;

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
              !d ? 'date-picker__cell--empty' : '',
              d && isPast(d) ? 'date-picker__cell--past' : '',
              d && isToday(d) && !isSelected(d) ? 'date-picker__cell--today' : '',
              d && isSelected(d) ? 'date-picker__cell--selected' : '',
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
 * On submit, a single `POST /api/v1/reservations/booking` call is made.
 * The backend creates the ReservationGroup and all ReservInstances atomically.
 * No client-side weekly loop.
 */
export default function ReservaModal({ open, onClose, initialStart = null, initialEnd = null }) {
  const {
    step,
    setStep,
    pickedDate,
    roomId,
    className,
    setClassName,
    startLabel,
    endLabel,
    setEndLabel,
    attendees,
    setAttendees,
    recurring,
    repeatUntil,
    setRepeatUntil,
    selectedDays,
    availableRooms,
    room,
    startSlots,
    endSlots,
    canSubmit,
    maxAttendees,
    semesterEnd,
    formatDate,
    handleDatePick,
    handleRecurringToggle,
    toggleDay,
    handleRoomChange,
    handleStartChange,
    handleSubmit,
    createBookingMutation,
  } = useReservaModal({ open, onClose, initialStart, initialEnd });

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
                  Reserva recurrente
                </span>
                <span className="reserva-modal__recurrente-desc">
                  Se agendará en los días seleccionados hasta la fecha indicada
                </span>
              </div>
              <button
                type="button"
                role="switch"
                aria-checked={recurring}
                className={`reserva-modal__toggle${recurring ? ' reserva-modal__toggle--on' : ''}`}
                onClick={handleRecurringToggle}
              />
            </div>

            {/* Recurring extras (only when toggle is ON) */}
            {recurring && (
              <>
                {/* Weekday checkboxes */}
                <div className="reserva-modal__field">
                  <label className="reserva-modal__label">Repetir los días*</label>
                  <div className="reserva-modal__weekdays">
                    {WEEKDAY_OPTIONS.map(wd => (
                      <button
                        key={wd.value}
                        type="button"
                        className={`reserva-modal__weekday-btn${selectedDays.includes(wd.value) ? ' reserva-modal__weekday-btn--active' : ''}`}
                        onClick={() => toggleDay(wd.value)}
                      >
                        {wd.label}
                      </button>
                    ))}
                  </div>
                </div>
              </>
            )}

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
                {createBookingMutation.isPending ? 'Reservando…' : 'Reservar Aula'}
              </button>
            </footer>
          </form>
        )}
      </div>
    </Modal>
  );
}
