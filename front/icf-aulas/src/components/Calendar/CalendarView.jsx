import { useRef, useState } from 'react';
import FullCalendar from '@fullcalendar/react';
import dayGridPlugin from '@fullcalendar/daygrid';
import timeGridPlugin from '@fullcalendar/timegrid';
import interactionPlugin from '@fullcalendar/interaction';

import './CalendarView.css';

const MONTHS_ES = [
  'Enero','Febrero','Marzo','Abril','Mayo','Junio',
  'Julio','Agosto','Septiembre','Octubre','Noviembre','Diciembre',
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

  const events = [
    {
      title: 'Reunión de trabajo',
      start: new Date(new Date().setHours(8, 0, 0, 0)),
      end:   new Date(new Date().setHours(10, 0, 0, 0)),
    },
    {
      title: 'Clase Magistral',
      start: new Date(new Date().setHours(10, 30, 0, 0)),
      end:   new Date(new Date().setHours(12, 0, 0, 0)),
      color: '#7c3aed',
    },
  ];

  const handleDatesSet = (info) => {
    setCurrentView(info.view.type);
    if (info.view.type === 'timeGridWeek') {
      setTitle(formatWeekTitle(info.start, info.end));
    } else {
      setTitle(formatMonthTitle(info.start));
    }
  };

  const goTo = (action) => {
    const api = calRef.current?.getApi();
    if (!api) return;
    if (action === 'prev')          api.prev();
    else if (action === 'next')     api.next();
    else if (action === 'today')    api.today();
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
          <button className="cal-toolbar__nav-btn" onClick={() => goTo('prev')} aria-label="Anterior">
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
          <button className="cal-toolbar__icon-btn" aria-label="Buscar">
            <i className="bi bi-search" />
          </button>
          <button className="cal-toolbar__today-btn" onClick={() => goTo('today')}>
            Hoy
          </button>
        </div>
      </div>

      {/* FullCalendar */}
      <div className="calendar-container">
        <FullCalendar
          ref={calRef}
          plugins={[dayGridPlugin, timeGridPlugin, interactionPlugin]}
          initialView="timeGridWeek"
          headerToolbar={false}
          events={events}
          locale="es"
          slotMinTime="07:00:00"
          slotMaxTime="20:00:00"
          allDaySlot={true}
          allDayText="Todo el día"
          height="100%"
          slotLabelFormat={{ hour: 'numeric', minute: '2-digit', hour12: true }}
          slotLabelInterval="00:30:00"
          dayHeaderContent={dayHeaderContent}
          datesSet={handleDatesSet}
          eventTimeFormat={{ hour: 'numeric', minute: '2-digit', hour12: true }}
        />
      </div>
    </div>
  );
}
