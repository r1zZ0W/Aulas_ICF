import { useState, useMemo, useCallback, useEffect } from 'react';
import FullCalendar from '@fullcalendar/react';
import dayGridPlugin from '@fullcalendar/daygrid';
import timeGridPlugin from '@fullcalendar/timegrid';
import interactionPlugin from '@fullcalendar/interaction';
import { useQuery } from '@tanstack/react-query';

import { useCalendar } from '../../hooks/useCalendar';
import { useReservation } from '../../context/ReservationContext';
import { getAvailability } from '../../api/reservations';
import { instanceToEvent } from '../../utils/reservations';
import DayEventsModal from './DayEventsModal';

import './CalendarView.css';

const dayHeaderContent = (args) => {
  const wd = args.date
    .toLocaleDateString('es-MX', { weekday: 'short', timeZone: 'UTC' })
    .replace('.', '')
    .toUpperCase()
    .slice(0, 3);

  if (args.view?.type === 'dayGridMonth') {
    return (
      <div className="cal-day-header">
        <span className="cal-day-header__wd">{wd}</span>
      </div>
    );
  }

  // En la vista semanal/diaria, mostramos el día de la semana y el número del día (en UTC para evitar desfases de zona horaria)
  const day = args.date.getUTCDate();
  return (
    <div className="cal-day-header">
      <span className="cal-day-header__wd">{wd}</span>
      <span className={`cal-day-header__d${args.isToday ? ' cal-day-header__d--today' : ''}`}>
        {day}
      </span>
    </div>
  );
};

export default function CalendarView() {
  const { visibleRooms, roomById } = useReservation();

  // ── Calendar date range (updated on every FullCalendar navigation) ──────────
  // Kept LOCAL to this component: datesSet fires on every prev/next click, so
  // putting this in global context would re-render the entire provider subtree.
  const [calendarRange, setCalendarRange] = useState({ from: null, to: null });

  const handleRangeChange = useCallback((range) => {
    setCalendarRange(range);
  }, []);

  const {
    calRef,
    currentView,
    title,
    isPrevDisabled,
    overflowDay,
    setOverflowDay,
    selectAllow,
    handleSelect,
    handleDateClick,
    handleEventClick,
    handleEventClickFromModal,
    handleMoreLinkClick,
    handleDatesSet,
    goTo,
    changeView,
    openModal,
  } = useCalendar({ onRangeChange: handleRangeChange });

  // ── Availability query ────────────────────────────────────────────────────
  const { data: instances = [] } = useQuery({
    queryKey: ['reservations', 'availability', calendarRange.from, calendarRange.to],
    queryFn: () => getAvailability({ from: calendarRange.from, to: calendarRange.to }),
    enabled: Boolean(calendarRange.from && calendarRange.to),
    staleTime: 30_000,
  });

  // ── Periodic update state for current time ────────────────────────────────
  const [now, setNow] = useState(new Date());

  useEffect(() => {
    const interval = setInterval(() => {
      setNow(new Date());
    }, 60000);
    return () => clearInterval(interval);
  }, []);

  // ── Compute visible events from fetched instances ─────────────────────────
  const visibleEvents = useMemo(() => {
    const list = instances
      .filter(inst => visibleRooms.has(inst.classroomUuid))
      .map(inst => instanceToEvent(inst, roomById[inst.classroomUuid]?.color ?? '#64748b'));

    if (currentView === 'timeGridWeek') {
      const mins = now.getMinutes() >= 30 ? 30 : 0;
      const boundary = new Date(
        now.getFullYear(),
        now.getMonth(),
        now.getDate(),
        now.getHours(),
        mins,
        0
      );

      list.push({
        id: 'past-disabled-bg',
        start: '2020-01-01T00:00:00',
        end: boundary,
        display: 'background',
        classNames: ['disabled-time-slot'],
      });
    }

    return list;
  }, [instances, visibleRooms, roomById, now, currentView]);

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
