import { useRef, useState } from 'react';
import { useReservation } from '../context/ReservationContext';

const MONTHS_ES = [
  'Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
  'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre',
];

const formatWeekTitle = (start, endExclusive) => {
  const s = new Date(start);
  const e = new Date(endExclusive);
  e.setDate(e.getDate() - 1);
  if (s.getMonth() === e.getMonth()) {
    return `${s.getDate()} – ${e.getDate()} de ${MONTHS_ES[s.getMonth()]}, ${s.getFullYear()}`;
  }
  return `${s.getDate()} ${MONTHS_ES[s.getMonth()]} – ${e.getDate()} ${MONTHS_ES[e.getMonth()]}, ${s.getFullYear()}`;
};

const formatMonthTitle = (start) => {
  const s = new Date(start);
  return `${MONTHS_ES[s.getMonth()]} ${s.getFullYear()}`;
};

/**
 * Calendar interaction logic for FullCalendar.
 *
 * @param {object}   [opts]
 * @param {Function} [opts.onRangeChange] - Called with `{ from, to }` (YYYY-MM-DD strings)
 *                                          whenever FullCalendar's visible date range changes.
 *                                          Used by CalendarView to trigger the availability query.
 */
export function useCalendar({ onRangeChange } = {}) {
  const calRef = useRef(null);
  const [currentView, setCurrentView] = useState('timeGridWeek');
  const [title, setTitle] = useState('');
  const { openModal, openInfoModal } = useReservation();
  const [overflowDay, setOverflowDay] = useState(null);
  const [isPrevDisabled, setIsPrevDisabled] = useState(false);

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

  const selectAllow = (info) => {
    const now = new Date();
    const boundary = new Date(now.getTime() + 15 * 60 * 1000);
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
      const now = new Date();
      const boundary = new Date(now.getTime() + 15 * 60 * 1000);
      if (info.date <= boundary) return;

      const startDate = new Date(info.date);
      const endDate = new Date(startDate.getTime() + 30 * 60 * 1000);
      openModal(startDate, endDate);
    }
  };

  /**
   * Handles a click on an event rendered directly in FullCalendar.
   * Extracts the full ReservInstanceResponseDTO from extendedProps and opens the info modal.
   */
  const handleEventClick = (info) => {
    info.jsEvent.preventDefault();
    const instance = info.event.extendedProps?.instance ?? null;
    openInfoModal(instance);
  };

  /**
   * Handles a click on an event inside DayEventsModal (overflow list).
   * `evt` is a FullCalendar event object (from allSegs); extendedProps.instance holds the DTO.
   */
  const handleEventClickFromModal = (evt) => {
    setOverflowDay(null);
    openInfoModal(evt?.extendedProps?.instance ?? null);
  };

  const handleMoreLinkClick = (info) => {
    info.jsEvent?.preventDefault();
    info.jsEvent?.stopPropagation();
    const evts = info.allSegs.map(seg => seg.event);
    setOverflowDay({ date: info.date, events: evts });
    return 'stop';
  };

  /**
   * Fired by FullCalendar on every view switch and prev/next navigation.
   * Reports the new visible date range to the parent (CalendarView) via `onRangeChange`
   * so the availability query can be updated without putting the range in global context
   * (which would re-render the entire provider subtree on every navigation click).
   */
  const handleDatesSet = (info) => {
    setCurrentView(info.view.type);

    // Compute local YYYY-MM-DD strings — safe against timezone shifts
    const start = info.start;
    const end   = info.end;
    const toStr = (d) => {
      const y = d.getFullYear();
      const m = String(d.getMonth() + 1).padStart(2, '0');
      const dd = String(d.getDate()).padStart(2, '0');
      return `${y}-${m}-${dd}`;
    };
    onRangeChange?.({ from: toStr(start), to: toStr(end) });

    if (info.view.type === 'timeGridWeek') {
      setTitle(formatWeekTitle(start, end));
      const currentWeekStart = getStartOfCurrentWeek();
      setIsPrevDisabled(start <= currentWeekStart);
    } else {
      setTitle(formatMonthTitle(info.view.currentStart));
      const currentMonthStart = getStartOfCurrentMonth();
      setIsPrevDisabled(info.view.currentStart <= currentMonthStart);
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

  return {
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
  };
}
