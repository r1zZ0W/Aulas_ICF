import { useState } from 'react';
import './MiniCalendar.css';

const DAYS = ['D', 'L', 'M', 'M', 'J', 'V', 'S'];
const MONTHS = [
  'Enero','Febrero','Marzo','Abril','Mayo','Junio',
  'Julio','Agosto','Septiembre','Octubre','Noviembre','Diciembre',
];

export default function MiniCalendar() {
  const today = new Date();
  const [viewDate, setViewDate] = useState(
    new Date(today.getFullYear(), today.getMonth(), 1)
  );

  const year = viewDate.getFullYear();
  const month = viewDate.getMonth();
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const firstDay = new Date(year, month, 1).getDay();

  const prevMonth = () => setViewDate(d => new Date(d.getFullYear(), d.getMonth() - 1, 1));
  const nextMonth = () => setViewDate(d => new Date(d.getFullYear(), d.getMonth() + 1, 1));

  const isToday = (d) =>
    d === today.getDate() && month === today.getMonth() && year === today.getFullYear();

  const isCurrentWeek = (d) => {
    const date = new Date(year, month, d);
    const sun = new Date(today);
    sun.setDate(today.getDate() - today.getDay());
    sun.setHours(0, 0, 0, 0);
    const sat = new Date(sun);
    sat.setDate(sun.getDate() + 6);
    sat.setHours(23, 59, 59, 999);
    return date >= sun && date <= sat;
  };

  const cells = [];
  for (let i = 0; i < firstDay; i++) cells.push(null);
  for (let d = 1; d <= daysInMonth; d++) cells.push(d);

  return (
    <div className="mini-cal">
      <div className="mini-cal__header">
        <span className="mini-cal__month">{MONTHS[month]} {year}</span>
        <div className="mini-cal__nav">
          <button className="mini-cal__nav-btn" onClick={prevMonth} aria-label="Mes anterior">
            <i className="bi bi-chevron-left" />
          </button>
          <button className="mini-cal__nav-btn" onClick={nextMonth} aria-label="Siguiente mes">
            <i className="bi bi-chevron-right" />
          </button>
        </div>
      </div>

      <div className="mini-cal__grid">
        {DAYS.map((d, i) => (
          <span key={i} className="mini-cal__weekday">{d}</span>
        ))}
        {cells.map((d, i) => (
          <span
            key={i}
            className={[
              'mini-cal__cell',
              !d                           ? 'mini-cal__cell--empty'  : '',
              d && isToday(d)              ? 'mini-cal__cell--today'  : '',
              d && !isToday(d) && isCurrentWeek(d) ? 'mini-cal__cell--week' : '',
            ].filter(Boolean).join(' ')}
          >
            {d}
          </span>
        ))}
      </div>
    </div>
  );
}
