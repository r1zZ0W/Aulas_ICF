import { createContext, useContext, useState, useCallback, useMemo, useEffect } from 'react';
import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { getClassrooms } from '../api/classrooms';
import { buildRoomsFromClassrooms } from '../utils/salas';

const ReservationContext = createContext(null);

// ── Demo-event seeder ─────────────────────────────────────────────────────────

/**
 * Generates a set of sample calendar events distributed across the provided
 * real room UUIDs so the calendar is never empty in dev / demo mode.
 * These events will be replaced by real API data once the reservations
 * backend is connected.
 *
 * @param {Array<{ uuid: string }>} rooms - Active room list from the backend.
 * @returns {Array<{
 *   id:         number,
 *   roomId:     string,
 *   title:      string,
 *   start:      Date,
 *   end:        Date,
 *   recurrente: boolean,
 * }>} Sample reservation events.
 */
function buildSampleEvents(rooms) {
  if (!rooms.length) return [];

  const today = new Date();

  /** @param {number} h @param {number} m @returns {Date} */
  const d = (h, m) =>
    new Date(today.getFullYear(), today.getMonth(), today.getDate(), h, m, 0);

  /**
   * @param {number} days
   * @param {number} h
   * @param {number} m
   * @returns {Date}
   */
  const offset = (days, h, m) => {
    const dt = new Date(today);
    dt.setDate(dt.getDate() + days);
    dt.setHours(h, m, 0, 0);
    return dt;
  };

  /** Cycles through real room UUIDs so events are spread across all rooms. */
  const pick = (i) => rooms[i % rooms.length].uuid;

  return [
    { id: 1, roomId: pick(0), title: 'Reunión de trabajo',    start: d(8, 0),         end: d(10, 0),          recurrente: false },
    { id: 2, roomId: pick(2), title: 'Clase Magistral',       start: d(10, 30),        end: d(12, 0),          recurrente: true  },
    { id: 3, roomId: pick(1), title: 'Taller de Innovación',  start: offset(1, 9, 0),  end: offset(1, 11, 0),  recurrente: true  },
    { id: 4, roomId: pick(3), title: 'Capacitación Docente',  start: offset(2, 14, 0), end: offset(2, 16, 0),  recurrente: false },
    { id: 5, roomId: pick(4), title: 'Seminario de Tesis',    start: offset(-1, 8, 0), end: offset(-1, 10, 0), recurrente: true  },
    { id: 6, roomId: pick(0), title: 'Conferencia Magistral', start: offset(3, 11, 0), end: offset(3, 13, 0),  recurrente: false },
  ];
}

// ── Provider ──────────────────────────────────────────────────────────────────

/**
 * Provides reservation and classroom-visibility state to all private-layout
 * descendants. Must be rendered inside {@link QueryClientProvider} and
 * {@link AuthProvider}.
 *
 * Exposes:
 *  - `rooms` / `roomById` — active classroom list and lookup map loaded from
 *    the backend; keyed under `['classrooms', …]` so any classroom mutation
 *    automatically refreshes the sidebar.
 *  - `visibleRooms` / `toggleRoom` — calendar room-visibility toggle state.
 *  - `reservations` / `addReservation` / `updateReservation` — local
 *    reservation list (demo data; will be replaced by real API calls).
 *  - `visibleEvents` — pre-filtered and pre-colored events for FullCalendar.
 *  - Modal open/close helpers for the reservation, info, and reschedule modals.
 *
 * @param {{ children: React.ReactNode }} props
 */
export function ReservationProvider({ children }) {
  // ── Active classroom list (real data from backend) ────────────────────────
  // Key is nested under ['classrooms', …] so any mutation on the classrooms
  // page (create / update / delete) automatically invalidates this query
  // and the sidebar re-renders with the updated list.
  const { data: pageData } = useQuery({
    queryKey:        ['classrooms', 'active', 'sidebar'],
    queryFn:         () => getClassrooms({ size: 200, sort: 'name', direction: 'asc' }),
    placeholderData: keepPreviousData,
    staleTime:       60_000,
  });

  /**
   * Active rooms ready for display (label, color, capacity, type).
   * Inactive classrooms are excluded by {@link buildRoomsFromClassrooms}.
   *
   * @type {Array<{ uuid: string, label: string, color: string, capacity: number, type: string }>}
   */
  const rooms = useMemo(
    () => buildRoomsFromClassrooms(pageData?.items ?? []),
    [pageData]
  );

  /**
   * Lookup map from room UUID → room display object.
   * Used by reservation modals to resolve a `roomId` to its display data.
   *
   * @type {Record<string, { uuid: string, label: string, color: string, capacity: number, type: string }>}
   */
  const roomById = useMemo(
    () => Object.fromEntries(rooms.map(r => [r.uuid, r])),
    [rooms]
  );

  // ── Room visibility toggles ───────────────────────────────────────────────

  /**
   * Set of room UUIDs currently visible in the calendar.
   * Initialized to "all rooms" once the real room list first arrives.
   * Subsequent list updates (e.g. a new room was created) do NOT reset the
   * user's visibility choices; they keep their current selection.
   *
   * @type {Set<string>}
   */
  const [visibleRooms, setVisibleRooms] = useState(new Set());

  useEffect(() => {
    if (rooms.length === 0) return;
    setVisibleRooms(prev => {
      if (prev.size > 0) return prev; // user already made selections — keep them
      return new Set(rooms.map(r => r.uuid));
    });
  }, [rooms]);

  // ── Reservations (demo data seeded from real rooms) ───────────────────────

  /** @type {[Array<object>, Function]} */
  const [reservations, setReservations] = useState([]);

  /** Ensures demo events are seeded exactly once after rooms load. */
  const [seeded, setSeeded] = useState(false);

  useEffect(() => {
    if (seeded || rooms.length === 0) return;
    setReservations(buildSampleEvents(rooms));
    setSeeded(true);
  }, [rooms, seeded]);

  // ── Modal state ───────────────────────────────────────────────────────────

  const [selectedDate,        setSelectedDate]        = useState(null);
  const [modalOpen,           setModalOpen]           = useState(false);
  const [modalSlot,           setModalSlot]           = useState({ start: null, end: null });
  const [infoModalOpen,       setInfoModalOpen]       = useState(false);
  const [selectedReservation, setSelectedReservation] = useState(null);
  const [rescheduleOpen,      setRescheduleOpen]      = useState(false);

  // ── Modal callbacks ───────────────────────────────────────────────────────

  /**
   * Opens the "Nueva Reserva" modal, optionally pre-filled with a time slot.
   *
   * @param {Date|null} [start=null] - Pre-selected start time.
   * @param {Date|null} [end=null]   - Pre-selected end time.
   */
  const openModal = useCallback((start = null, end = null) => {
    setModalSlot({ start, end });
    if (start) setSelectedDate(new Date(start));
    setModalOpen(true);
  }, []);

  /** Closes the "Nueva Reserva" modal. */
  const closeModal = useCallback(() => setModalOpen(false), []);

  /**
   * Opens the reservation info modal for the event with the given id.
   *
   * @param {string|number} eventId - The id of the reservation to display.
   */
  const openInfoModal = useCallback((eventId) => {
    const reservation = reservations.find(r => String(r.id) === String(eventId));
    if (!reservation) return;
    setSelectedReservation(reservation);
    setInfoModalOpen(true);
  }, [reservations]);

  /** Closes the reservation info modal. */
  const closeInfoModal = useCallback(() => setInfoModalOpen(false), []);

  /** Closes the info modal and opens the reschedule (reasignar) modal. */
  const openReschedule = useCallback(() => {
    setInfoModalOpen(false);
    setRescheduleOpen(true);
  }, []);

  /** Closes the reschedule modal and clears the selected reservation. */
  const closeReschedule = useCallback(() => {
    setRescheduleOpen(false);
    setSelectedReservation(null);
  }, []);

  /**
   * Toggles the visibility of a room in the calendar.
   *
   * Behaviour:
   *  - Clicking the only visible room shows all rooms again (solo → all).
   *  - Clicking any other room isolates it (shows only that one).
   *
   * @param {string} uuid - UUID of the room to toggle.
   */
  const toggleRoom = useCallback((uuid) => {
    setVisibleRooms(prev => {
      const allUuids = rooms.map(r => r.uuid);
      if (prev.has(uuid) && prev.size === 1) return new Set(allUuids);
      return new Set([uuid]);
    });
  }, [rooms]);

  /**
   * Adds one or more reservations to the local list.
   *
   * @param {object|object[]} reservationOrArray - Single reservation or array of reservations.
   */
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

  /**
   * Merges `changes` into the reservation identified by `id`.
   *
   * @param {number} id      - Reservation id.
   * @param {object} changes - Partial fields to overwrite.
   */
  const updateReservation = useCallback((id, changes) => {
    setReservations(prev => prev.map(r => r.id === id ? { ...r, ...changes } : r));
  }, []);

  /**
   * Reservations filtered to only those whose room is currently visible,
   * with the room color injected for FullCalendar rendering.
   *
   * @type {Array<object>}
   */
  const visibleEvents = useMemo(() =>
    reservations
      .filter(r => visibleRooms.has(r.roomId))
      .map(r => ({
        ...r,
        color: roomById[r.roomId]?.color ?? '#64748b',
      })),
    [reservations, visibleRooms, roomById]
  );

  return (
    <ReservationContext.Provider value={{
      // Classroom catalog
      rooms,
      roomById,
      // Calendar date
      selectedDate,
      setSelectedDate,
      // Nueva reserva modal
      modalOpen,
      openModal,
      closeModal,
      modalSlot,
      // Room visibility toggles (sidebar)
      visibleRooms,
      toggleRoom,
      // Reservations (demo / future API)
      reservations,
      addReservation,
      updateReservation,
      visibleEvents,
      // Reservation info modal
      infoModalOpen,
      selectedReservation,
      openInfoModal,
      closeInfoModal,
      // Reschedule (reasignar) modal
      rescheduleOpen,
      openReschedule,
      closeReschedule,
    }}>
      {children}
    </ReservationContext.Provider>
  );
}

/**
 * Hook to access the {@link ReservationContext}.
 * Must be used inside a {@link ReservationProvider}.
 *
 * @returns {ReturnType<typeof ReservationProvider>} Context value.
 * @throws {Error} If called outside of a {@link ReservationProvider}.
 */
export function useReservation() {
  const ctx = useContext(ReservationContext);
  if (!ctx) throw new Error('useReservation must be inside ReservationProvider');
  return ctx;
}
