import { createContext, useContext, useState, useCallback, useMemo, useEffect } from 'react';
import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { getClassrooms } from '../api/classrooms';
import {
  cancelReservation,
  cancelReservationAdmin,
  reassignReservation,
  createBooking,
} from '../api/reservations';
import { useApiMutation } from '../hooks/useApiMutation';
import { buildRoomsFromClassrooms } from '../utils/salas';

const ReservationContext = createContext(null);

// ── Provider ──────────────────────────────────────────────────────────────────

/**
 * Provides reservation and classroom-visibility state to all private-layout
 * descendants. Must be rendered inside {@link QueryClientProvider} and
 * {@link AuthProvider}.
 *
 * Exposes:
 *  - `rooms` / `roomById` — active classroom list and lookup map.
 *  - `visibleRooms` / `toggleRoom` — calendar room-visibility toggle state.
 *  - `selectedReservation` / `openInfoModal` / `closeInfoModal` — info modal state.
 *    `openInfoModal` now accepts a full `ReservInstanceResponseDTO` object (not an ID).
 *  - `studentsModalOpen` / `openStudentsModal` / `closeStudentsModal` — admin-only "view
 *    students" modal, a sub-flow of the info modal. `closeStudentsModal` reopens the info
 *    modal instead of clearing `selectedReservation`.
 *  - `createBookingMutation`, `cancelReservationMutation`, `cancelReservationAdminMutation`,
 *    `reassignMutation` — React Query mutations with automatic cache invalidation.
 *  - Modal open/close helpers for the create, info, and reassign modals.
 *
 * @param {{ children: React.ReactNode }} props
 */
export function ReservationProvider({ children }) {
  // ── Active classroom list ─────────────────────────────────────────────────
  const { data: pageData } = useQuery({
    queryKey: ['classrooms', 'active', 'sidebar'],
    queryFn: () => getClassrooms({ size: 200, sort: 'name', direction: 'asc' }),
    placeholderData: keepPreviousData,
    staleTime: 60_000,
  });

  /**
   * Active rooms ready for display (label, color, capacity, type).
   * Inactive classrooms are excluded by {@link buildRoomsFromClassrooms}.
   */
  const rooms = useMemo(
    () => buildRoomsFromClassrooms(pageData?.items ?? []),
    [pageData]
  );

  /**
   * Lookup map from room UUID → room display object.
   * Used by reservation modals to resolve a `classroomUuid` to its display data.
   */
  const roomById = useMemo(
    () => Object.fromEntries(rooms.map(r => [r.uuid, r])),
    [rooms]
  );

  // ── Room visibility toggles ───────────────────────────────────────────────

  /** Set of room UUIDs currently visible in the calendar. */
  const [visibleRooms, setVisibleRooms] = useState(new Set());

  useEffect(() => {
    if (rooms.length === 0) return;
    setVisibleRooms(prev => {
      if (prev.size > 0) return prev; // keep user's existing selection
      return new Set(rooms.map(r => r.uuid));
    });
  }, [rooms]);

  // ── Modal state ───────────────────────────────────────────────────────────

  const [selectedDate, setSelectedDate] = useState(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [modalSlot, setModalSlot] = useState({ start: null, end: null });
  const [infoModalOpen, setInfoModalOpen] = useState(false);
  const [selectedReservation, setSelectedReservation] = useState(null);
  const [rescheduleOpen, setRescheduleOpen] = useState(false);
  const [studentsModalOpen, setStudentsModalOpen] = useState(false);

  // ── Modal callbacks ───────────────────────────────────────────────────────

  /**
   * Opens the "Nueva Reserva" modal, optionally pre-filled with a time slot.
   *
   * @param {Date|null} [start=null]
   * @param {Date|null} [end=null]
   */
  const openModal = useCallback((start = null, end = null) => {
    setModalSlot({ start, end });
    if (start) setSelectedDate(new Date(start));
    setModalOpen(true);
  }, []);

  const closeModal = useCallback(() => setModalOpen(false), []);

  /**
   * Opens the reservation info modal for the given instance.
   *
   * @param {object|null} instance - Full ReservInstanceResponseDTO from the API (or null to close)
   */
  const openInfoModal = useCallback((instance) => {
    if (!instance) return;
    setSelectedReservation(instance);
    setInfoModalOpen(true);
  }, []);

  const closeInfoModal = useCallback(() => {
    setInfoModalOpen(false);
    setSelectedReservation(null);
  }, []);

  const openReschedule = useCallback(() => {
    setInfoModalOpen(false);
    setRescheduleOpen(true);
  }, []);

  const closeReschedule = useCallback(() => {
    setRescheduleOpen(false);
    setSelectedReservation(null);
  }, []);

  /**
   * Opens the "view students" modal (admin-only), swapping out the info modal.
   * `selectedReservation` is left untouched — this is a sub-flow of the detail view,
   * not a separate entry point.
   */
  const openStudentsModal = useCallback(() => {
    setInfoModalOpen(false);
    setStudentsModalOpen(true);
  }, []);

  /**
   * Closes the "view students" modal and returns to the reservation detail modal
   * (acts as "Volver", not a hard close). `selectedReservation` is only cleared by
   * {@link closeInfoModal}, when the admin actually leaves the detail flow.
   */
  const closeStudentsModal = useCallback(() => {
    setStudentsModalOpen(false);
    setInfoModalOpen(true);
  }, []);

  /**
   * Toggles the visibility of a room in the calendar.
   * Clicking the only visible room shows all rooms again (solo → all).
   */
  const toggleRoom = useCallback((uuid) => {
    setVisibleRooms(prev => {
      const allUuids = rooms.map(r => r.uuid);
      if (prev.has(uuid) && prev.size === 1) return new Set(allUuids);
      return new Set([uuid]);
    });
  }, [rooms]);

  // ── Mutations ─────────────────────────────────────────────────────────────

  // Segmented cache invalidation keys.
  // Using specific sub-keys avoids flushing unrelated queries while ensuring
  // both the calendar and the history table stay in sync after any mutation.
  // Rationale for INVALIDATE_BOTH on cancel/reassign:
  //   • Calendar  — freed/moved slots must no longer show as occupied.
  //   • History   — badge must update in real time (Cancelada / Reasignada).
  // createBooking only affects the calendar (new slot appears); the history
  // query will pick up the new entry on its next natural refetch.
  // ['timeslots', 'available'] is invalidated on every mutation that changes slot occupancy so
  // the "Nueva Reserva" modal reflects freed/occupied slots immediately if reopened.
  const INVALIDATE_CALENDAR = ['reservations', 'availability'];
  const INVALIDATE_HISTORY = ['reservations', 'history'];
  const INVALIDATE_AVAILABLE_SLOTS = ['timeslots', 'available'];
  const INVALIDATE_BOTH = [INVALIDATE_CALENDAR, INVALIDATE_HISTORY, INVALIDATE_AVAILABLE_SLOTS];

  /**
   * Creates a booking (group + all instances) atomically, sending the mandatory student
   * roster file in the same multipart request. Callers pass `{ payload, file }`.
   * Invalidates the calendar availability cache so new slots appear immediately.
   */
  const createBookingMutation = useApiMutation({
    mutationFn: ({ payload, file }) => createBooking(payload, file),
    invalidateKey: [INVALIDATE_CALENDAR, INVALIDATE_AVAILABLE_SLOTS],
    successMessage: 'Reserva creada exitosamente',
  });

  /**
   * Cancels a reservation as the owning teacher.
   * Invalidates calendar (freed slot) and history (badge → Cancelada) on success.
   * Closes the info modal on success.
   */
  const cancelReservationMutation = useApiMutation({
    mutationFn: (uuid) => cancelReservation(uuid),
    invalidateKey: INVALIDATE_BOTH,
    successMessage: 'Reserva cancelada',
    onSuccess: () => setInfoModalOpen(false),
  });

  /**
   * Cancels a reservation as an administrator.
   * Invalidates calendar (freed slot) and history (badge → Cancelada) on success.
   * Closes the info modal on success.
   */
  const cancelReservationAdminMutation = useApiMutation({
    mutationFn: (uuid) => cancelReservationAdmin(uuid),
    invalidateKey: INVALIDATE_BOTH,
    successMessage: 'Reserva cancelada por administrador',
    onSuccess: () => setInfoModalOpen(false),
  });

  /**
   * Reassigns an active reservation. Called as `reassignMutation.mutate({ uuid, ...payload })`.
   * Invalidates calendar (new slot location) and history (badge → Reasignada) on success.
   * Closes the reschedule modal on success.
   */
  const reassignMutation = useApiMutation({
    mutationFn: ({ uuid, ...payload }) => reassignReservation(uuid, payload),
    invalidateKey: INVALIDATE_BOTH,
    successMessage: 'Reserva reasignada exitosamente',
    onSuccess: () => setRescheduleOpen(false),
  });

  // ─────────────────────────────────────────────────────────────────────────

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
      // Reservation info modal
      infoModalOpen,
      selectedReservation,
      openInfoModal,
      closeInfoModal,
      // Reschedule (reasignar) modal
      rescheduleOpen,
      openReschedule,
      closeReschedule,
      // View students modal (admin-only)
      studentsModalOpen,
      openStudentsModal,
      closeStudentsModal,
      // Mutations (shared across modals)
      createBookingMutation,
      cancelReservationMutation,
      cancelReservationAdminMutation,
      reassignMutation,
    }}>
      {children}
    </ReservationContext.Provider>
  );
}

/**
 * Hook to access the {@link ReservationContext}.
 * Must be used inside a {@link ReservationProvider}.
 *
 * @throws {Error} If called outside of a {@link ReservationProvider}.
 */
export function useReservation() {
  const ctx = useContext(ReservationContext);
  if (!ctx) throw new Error('useReservation must be inside ReservationProvider');
  return ctx;
}
