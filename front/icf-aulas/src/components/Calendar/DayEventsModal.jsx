import { X, Clock, CalendarPlus } from 'lucide-react';
import Modal from '../Modal/Modal';
import './DayEventsModal.css';

const MONTHS_ES   = ['Enero','Febrero','Marzo','Abril','Mayo','Junio','Julio','Agosto','Septiembre','Octubre','Noviembre','Diciembre'];
const WEEKDAYS_ES = ['Domingo','Lunes','Martes','Miércoles','Jueves','Viernes','Sábado'];

function fmtTime(dateVal) {
  const d = dateVal instanceof Date ? dateVal : new Date(dateVal);
  return d.toLocaleTimeString('es-MX', { hour: 'numeric', minute: '2-digit', hour12: true });
}

export default function DayEventsModal({ open, onClose, date, events = [], onCreateReservation, onEventClick }) {
  if (!open || !date) return null;

  const today        = new Date();
  const todayMidnight = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  const isPast       = date < todayMidnight;

  const sorted = [...events].sort((a, b) => {
    const sa = a.start instanceof Date ? a.start : new Date(a.start);
    const sb = b.start instanceof Date ? b.start : new Date(b.start);
    return sa - sb;
  });

  return (
    <Modal open={open} className="day-events-modal">
      <div className="day-events-modal__inner">

        {/* Header */}
        <header className="day-events-modal__header">
          <div className="day-events-modal__header-date">
            <span className="day-events-modal__weekday">{WEEKDAYS_ES[date.getDay()]}</span>
            <span className="day-events-modal__day-num">{date.getDate()}</span>
            <span className="day-events-modal__month-year">
              {MONTHS_ES[date.getMonth()]} {date.getFullYear()}
            </span>
          </div>
          <button
            type="button"
            className="day-events-modal__close"
            onClick={onClose}
            aria-label="Cerrar"
          >
            <X size={20} strokeWidth={2.5} />
          </button>
        </header>

        {/* Event list */}
        <div className="day-events-modal__list">
          {sorted.length === 0 ? (
            <p className="day-events-modal__empty">No hay eventos para este día.</p>
          ) : (
            sorted.map((evt, i) => {
              const color = evt.backgroundColor || evt.color || evt.extendedProps?.color || '#005687';
              const hasTime = evt.start && !evt.allDay;
              return (
                <div
                  key={i}
                  className="day-events-modal__event"
                  onClick={() => onEventClick && onEventClick(evt.id)}
                >
                  <span className="day-events-modal__event-dot" style={{ background: color }} />
                  <div className="day-events-modal__event-body">
                    <span className="day-events-modal__event-title">{evt.title}</span>
                    {hasTime && (
                      <span className="day-events-modal__event-time">
                        <Clock size={11} />
                        {fmtTime(evt.start)}
                        {evt.end ? ` – ${fmtTime(evt.end)}` : ''}
                      </span>
                    )}
                  </div>
                </div>
              );
            })
          )}
        </div>

        {/* Footer – only for non-past days */}
        {!isPast && (
          <footer className="day-events-modal__footer">
            <button
              type="button"
              className="day-events-modal__create-btn"
              onClick={() => { onClose(); onCreateReservation(date); }}
            >
              <CalendarPlus size={16} strokeWidth={2} />
              Nueva reserva para este día
            </button>
          </footer>
        )}
      </div>
    </Modal>
  );
}
