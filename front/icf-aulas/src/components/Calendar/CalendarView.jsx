import { useRef, useState } from 'react';
import FullCalendar from '@fullcalendar/react';
import dayGridPlugin from '@fullcalendar/daygrid';
import timeGridPlugin from '@fullcalendar/timegrid';
import interactionPlugin from '@fullcalendar/interaction';
import { useReservation } from '../../context/ReservationContext';
import DayEventsModal from './DayEventsModal';

import './CalendarView.css';

const MONTHS_ES = [
  'Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
  'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre',
];

function formatWeekTitle(start, endExclusive) {
  const s = new Date(start);
  const e = new Date(endExclusive);
  e.setDate(e.getDate() - 1);
  if (s.getMonth() === e.getMonth()) {
    return `${s.getDate()} – ${e.getDate()} de ${MONTHS_ES[s.getMonth()]}, ${s.getFullYear()}`;
  }
  return `${s.getDate()} ${MONTHS_ES[s.getMonth()]} – ${e.getDate()} ${MONTHS_ES[e.getMonth()]}, ${s.getFullYear()}`;
}

function formatMonthTitle(start) {
  const s = new Date(start);
  return `${MONTHS_ES[s.getMonth()]} ${s.getFullYear()}`;
}

export default function CalendarView() {
  const calRef = useRef(null);
  const [currentView, setCurrentView] = useState('timeGridWeek');
  const [title, setTitle] = useState('');
  const { openModal, visibleEvents, openInfoModal } = useReservation();

  const [overflowDay, setOverflowDay] = useState(null);

  const getStartOfCurrentWeek = () => {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const day = today.getDay();
    const diff = today.getDate() - (day === 0 ? 6 : day - 1);
    const monday = new Date(today.setDate(diff));
    monday.setHours(0, 0, 0, 0);
    return monday;
  };

  const getStartOfCurrentMonth = () => {
    const today = new Date();
    return new Date(today.getFullYear(), today.getMonth(), 1);
  };

  const [isPrevDisabled, setIsPrevDisabled] = useState(false);

  const selectAllow = (info) => {
    const now = new Date();
    const mins = now.getMinutes() >= 30 ? 30 : 0;
    const boundary = new Date(
      now.getFullYear(), now.getMonth(), now.getDate(),
      now.getHours(), mins, 0
    );
    return info.start > boundary;
  };

  const handleSelect = (info) => {
    openModal(info.start, info.end);
    calRef.current?.getApi().unselect();
  };

  const handleDateClick = (info) => {
    const today = new Date();
    const todayMidnight = new Date(today.getFullYear(), today.getMonth(), today.getDate());
    if (info.date < todayMidnight) return;

    if (currentView === 'dayGridMonth') {
      openModal(info.date);
    } else if (currentView === 'timeGridWeek') {
      const startDate = new Date(info.date);
      const endDate = new Date(startDate.getTime() + 30 * 60 * 1000);
      openModal(startDate, endDate);
    }
  };

  const handleEventClick = (info) => {
    info.jsEvent.preventDefault();
    openInfoModal(info.event.id);
  };

  const handleEventClickFromModal = (eventId) => {
    setOverflowDay(null);
    openInfoModal(eventId);
  };

  const handleMoreLinkClick = (info) => {
    const evts = info.allSegs.map(seg => seg.event);
    setOverflowDay({ date: info.date, events: evts });
    return false;
  };

  const handleDatesSet = (info) => {
    setCurrentView(info.view.type);
    if (info.view.type === 'timeGridWeek') {
      setTitle(formatWeekTitle(info.start, info.end));
      const currentWeekStart = getStartOfCurrentWeek();
      setIsPrevDisabled(info.start <= currentWeekStart);
    } else {
      setTitle(formatMonthTitle(info.start));
      const currentMonthStart = getStartOfCurrentMonth();
      setIsPrevDisabled(info.start <= currentMonthStart);
    }
  };

  const goTo = (action) => {
    if (action === 'prev' && isPrevDisabled) return;
    const api = calRef.current?.getApi();
    if (!api) return;
    if (action === 'prev') api.prev();
    else if (action === 'next') api.next();
    else if (action === 'today') api.today();
  };

  const changeView = (viewName) => {
    const api = calRef.current?.getApi();
    if (!api) return;
    api.changeView(viewName);
    setCurrentView(viewName);
  };

  const dayHeaderContent = (args) => {
    const wd = args.date
      .toLocaleDateString('es-MX', { weekday: 'short' })
      .replace('.', '')
      .toUpperCase()
      .slice(0, 3);
    const day = args.date.getDate();
    return (
      <div className="cal-day-header">
        <span className="cal-day-header__wd">{wd}</span>
        <span className={`cal-day-header__d${args.isToday ? ' cal-day-header__d--today' : ''}`}>
          {day}
        </span>
      </div>
    );
  };

  return (
    <div className="calendar-wrapper">
      {/* Custom toolbar */}
      <div className="cal-toolbar">
        <div className="cal-toolbar__left">
          <button className="cal-toolbar__icon-btn" aria-label="Menú">
            <i className="bi bi-list" />
          </button>
          <button
            className={`cal-toolbar__nav-btn${isPrevDisabled ? ' cal-toolbar__nav-btn--disabled' : ''}`}
            onClick={() => goTo('prev')}
            disabled={isPrevDisabled}
            aria-label="Anterior"
          >
            <i className="bi bi-chevron-left" />
          </button>
          <button className="cal-toolbar__nav-btn" onClick={() => goTo('next')} aria-label="Siguiente">
            <i className="bi bi-chevron-right" />
          </button>
          <h2 className="cal-toolbar__title">{title}</h2>
        </div>

        <div className="cal-toolbar__right">
          <div className="cal-toolbar__view-toggle">
            <button
              className={`cal-toolbar__view-btn${currentView === 'timeGridWeek' ? ' cal-toolbar__view-btn--active' : ''}`}
              onClick={() => changeView('timeGridWeek')}
            >
              Semana
            </button>
            <button
              className={`cal-toolbar__view-btn${currentView === 'dayGridMonth' ? ' cal-toolbar__view-btn--active' : ''}`}
              onClick={() => changeView('dayGridMonth')}
            >
              Mes
            </button>
          </div>
          <button className="cal-toolbar__today-btn" onClick={() => goTo('today')}>
            Hoy
          </button>
        </div>
      </div>

      {/* FullCalendar */}
      <div className="calendar-container">
        <FullCalendar
          ref={calRef}
          firstDay={1}
          plugins={[dayGridPlugin, timeGridPlugin, interactionPlugin]}
          initialView="timeGridWeek"
          headerToolbar={false}
          events={visibleEvents}
          locale="es"
          slotMinTime="07:00:00"
          slotMaxTime="19:30:00"
          slotDuration="00:30:00"
          snapDuration="00:30:00"
          allDaySlot={true}
          allDayText="Todo el día"
          height="100%"
          slotLabelFormat={{ hour: 'numeric', minute: '2-digit', hour12: true }}
          slotLabelInterval="00:30:00"
          dayHeaderContent={dayHeaderContent}
          datesSet={handleDatesSet}
          eventTimeFormat={{ hour: 'numeric', minute: '2-digit', hour12: true }}
          selectable={true}
          selectMirror={true}
          selectAllow={selectAllow}
          select={handleSelect}
          dateClick={handleDateClick}
          eventClick={handleEventClick}
          dayMaxEvents={4}
          moreLinkClick={handleMoreLinkClick}
          moreLinkContent={(args) => `+${args.num} más`}
        />
      </div>

      {/* Overflow day modal */}
      <DayEventsModal
        open={overflowDay !== null}
        onClose={() => setOverflowDay(null)}
        date={overflowDay?.date ?? null}
        events={overflowDay?.events ?? []}
        onCreateReservation={(date) => openModal(date)}
        onEventClick={handleEventClickFromModal}
      />
    </div>
  );
}
