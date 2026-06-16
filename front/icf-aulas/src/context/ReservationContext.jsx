import { createContext, useContext, useState, useCallback, useMemo } from 'react';
import { SALAS, SALA_BY_ID } from '../utils/salas';

const ALL_SALA_IDS = SALAS.map(s => s.id);

function buildSampleEvents() {
  const today = new Date();
  const d = (h, m) => new Date(today.getFullYear(), today.getMonth(), today.getDate(), h, m, 0);

  const offset = (days, h, m) => {
    const dt = new Date(today);
    dt.setDate(dt.getDate() + days);
    dt.setHours(h, m, 0, 0);
    return dt;
  };

  return [
    { id: 1, salaId: 1, title: 'Reunión de trabajo', start: d(8, 0), end: d(10, 0), recurrente: false },
    { id: 2, salaId: 3, title: 'Clase Magistral', start: d(10, 30), end: d(12, 0), recurrente: true },
    { id: 3, salaId: 2, title: 'Taller de Innovación', start: offset(1, 9, 0), end: offset(1, 11, 0), recurrente: true },
    { id: 4, salaId: 4, title: 'Capacitación Docente', start: offset(2, 14, 0), end: offset(2, 16, 0), recurrente: false },
    { id: 5, salaId: 5, title: 'Seminario de Tesis', start: offset(-1, 8, 0), end: offset(-1, 10, 0), recurrente: true },
    { id: 6, salaId: 1, title: 'Conferencia Magistral', start: offset(3, 11, 0), end: offset(3, 13, 0), recurrente: false },
  ];
}

const ReservationContext = createContext(null);

export function ReservationProvider({ children }) {
  const [selectedDate, setSelectedDate] = useState(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [modalSlot, setModalSlot] = useState({ start: null, end: null });
  const [visibleSalas, setVisibleSalas] = useState(new Set(ALL_SALA_IDS));
  const [reservations, setReservations] = useState(buildSampleEvents);

  // Info modal (read-only view of existing reservation)
  const [infoModalOpen, setInfoModalOpen] = useState(false);
  const [selectedReservation, setSelectedReservation] = useState(null);

  // Reasignar modal (admin edit form)
  const [reasignarOpen, setReasignarOpen] = useState(false);

  const openModal = useCallback((start = null, end = null) => {
    setModalSlot({ start, end });
    if (start) setSelectedDate(new Date(start));
    setModalOpen(true);
  }, []);

  const closeModal = useCallback(() => {
    setModalOpen(false);
  }, []);

  const openInfoModal = useCallback((eventId) => {
    const reservation = reservations.find(r => String(r.id) === String(eventId));
    if (!reservation) return;
    setSelectedReservation(reservation);
    setInfoModalOpen(true);
  }, [reservations]);

  const closeInfoModal = useCallback(() => {
    setInfoModalOpen(false);
  }, []);

  const openReasignar = useCallback(() => {
    setInfoModalOpen(false);
    setReasignarOpen(true);
  }, []);

  const closeReasignar = useCallback(() => {
    setReasignarOpen(false);
    setSelectedReservation(null);
  }, []);

  /**
   * Toggles the visibility of a classroom.
   * @param {number} id - The ID of the classroom.
   * @returns {Set<number>} - A set of visible classroom IDs.
   */
  const toggleSala = useCallback((id) => {
    setVisibleSalas(prev => {

      // If we are selecting the same classroom, we will show all the classrooms again.
      if (prev.has(id) && prev.size === 1) {
        return new Set(ALL_SALA_IDS)
      }

      // Otherwise, it will only show the selected classroom.
      return new Set([id])
    });
  }, []);

  const addReservation = useCallback((reservationOrArray) => {
    setReservations(prev => {
      const items = Array.isArray(reservationOrArray) ? reservationOrArray : [reservationOrArray];
      const newItems = items.map((item, idx) => ({
        ...item,
        id: item.id || (Date.now() + idx),
      }));
      return [...prev, ...newItems];
    });
  }, []);

  const updateReservation = useCallback((id, changes) => {
    setReservations(prev => prev.map(r => r.id === id ? { ...r, ...changes } : r));
  }, []);

  const visibleEvents = useMemo(() =>
    reservations
      .filter(r => visibleSalas.has(r.salaId))
      .map(r => ({
        ...r,
        color: SALA_BY_ID[r.salaId]?.color ?? '#64748b',
      })),
    [reservations, visibleSalas]
  );

  return (
    <ReservationContext.Provider value={{
      selectedDate,
      setSelectedDate,
      modalOpen,
      openModal,
      closeModal,
      modalSlot,
      visibleSalas,
      toggleSala,
      reservations,
      addReservation,
      updateReservation,
      visibleEvents,
      infoModalOpen,
      selectedReservation,
      openInfoModal,
      closeInfoModal,
      reasignarOpen,
      openReasignar,
      closeReasignar,
    }}>
      {children}
    </ReservationContext.Provider>
  );
}

export function useReservation() {
  const ctx = useContext(ReservationContext);
  if (!ctx) throw new Error('useReservation must be inside ReservationProvider');
  return ctx;
}
