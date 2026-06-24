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
 *  - `createBookingMutation`, `cancelReservationMutation`, `cancelReservationAdminMutation`,
 *    `reassignMutation` — React Query mutations with automatic cache invalidation.
 *  - Modal open/close helpers for the create, info, and reassign modals.
 *
 * @param {{ children: React.ReactNode }} props
 */
export function ReservationProvider({ children }) {
  // ── Active classroom list ─────────────────────────────────────────────────
  const { data: pageData } = useQuery({
    queryKey:        ['classrooms', 'active', 'sidebar'],
    queryFn:         () => getClassrooms({ size: 200, sort: 'name', direction: 'asc' }),
    placeholderData: keepPreviousData,
    staleTime:       60_000,
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

  /**
   * Creates a booking (group + all instances) atomically.
   * Invalidates ['reservations'] on success so the calendar refreshes.
   */
  const createBookingMutation = useApiMutation({
    mutationFn:     createBooking,
    invalidateKey:  ['reservations'],
    successMessage: 'Reserva creada exitosamente',
  });

  /**
   * Cancels a reservation as the owning teacher.
   * Closes the info modal on success.
   */
  const cancelReservationMutation = useApiMutation({
    mutationFn:     (uuid) => cancelReservation(uuid),
    invalidateKey:  ['reservations'],
    successMessage: 'Reserva cancelada',
    onSuccess:      () => setInfoModalOpen(false),
  });

  /**
   * Cancels a reservation as an administrator.
   * Closes the info modal on success.
   */
  const cancelReservationAdminMutation = useApiMutation({
    mutationFn:     (uuid) => cancelReservationAdmin(uuid),
    invalidateKey:  ['reservations'],
    successMessage: 'Reserva cancelada por administrador',
    onSuccess:      () => setInfoModalOpen(false),
  });

  /**
   * Reassigns an active reservation. Called as `reassignMutation.mutate({ uuid, ...payload })`.
   * Closes the reschedule modal on success.
   */
  const reassignMutation = useApiMutation({
    mutationFn:     ({ uuid, ...payload }) => reassignReservation(uuid, payload),
    invalidateKey:  ['reservations'],
    successMessage: 'Reserva reasignada exitosamente',
    onSuccess:      () => setRescheduleOpen(false),
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
