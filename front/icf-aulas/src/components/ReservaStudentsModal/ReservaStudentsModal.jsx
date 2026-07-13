import { X, Users, TriangleAlert } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import Modal from '../Modal/Modal';
import DataTable from '../DataTable/DataTable';
import EmptyState from '../EmptyState/EmptyState';
import ErrorBanner from '../ErrorBanner/ErrorBanner';
import { getReservationStudents } from '../../api/reservations';
import '../ReservaModal/ReservaModal.css';
import './ReservaStudentsModal.css';

const columns = [
  {
    key: 'fullName',
    header: 'Nombre completo',
    render: (row) => `${row.firstName} ${row.lastName}`.trim(),
  },
  {
    key: 'email',
    header: 'Correo',
    render: (row) => row.email,
  },
];

// ── Component ─────────────────────────────────────────────────────────────────

/**
 * Admin-only, read-only modal listing the students registered for a reservation's
 * roster. Reads `reservation.groupUuid` (the roster is per-*group*, shared across every
 * date occurrence) and `reservation.attendeeCount` (already present on the reservation
 * DTO — not part of the roster response).
 *
 * The total attendee count is shown in a summary card, never inside the table: the
 * table renders only the roster's students. Because the roster lives as a loose file on
 * disk, its actual row count can drift from `attendeeCount` (manual edits, legacy data);
 * when that happens a subtle inline notice is shown instead of breaking the layout.
 *
 * `onClose` is provided by the caller (`ReservationContext.closeStudentsModal`), which
 * reopens the reservation detail modal rather than dropping the user back to the
 * calendar/history view — this modal is a sub-flow of the detail view, not a dead end.
 *
 * @param {{
 *   open:        boolean,
 *   onClose:     () => void,
 *   reservation: object | null,
 * }} props
 */
export default function ReservaStudentsModal({ open, onClose, reservation }) {
  const groupUuid = reservation?.groupUuid;

  const {
    data: students = [],
    isLoading,
    isError,
    error,
    refetch,
  } = useQuery({
    queryKey: ['reservations', groupUuid, 'students'],
    queryFn: () => getReservationStudents(groupUuid),
    enabled: open && !!groupUuid,
    staleTime: 60_000,
    gcTime: 300_000,
  });

  if (!open || !reservation) return null;

  const attendeeCount = reservation.attendeeCount ?? 0;
  // The roster file and the reservation's attendeeCount are two independently-stored
  // facts (a loose .xlsx vs. a DB column) — they can drift apart. Surface it, don't hide it.
  const rosterMismatch = !isLoading && !isError && students.length !== attendeeCount;

  return (
    <Modal open={open} className="reserva-students-modal">
      <div className="reserva-modal__inner">

        <header className="reserva-modal__header">
          <h2 className="reserva-modal__title">Estudiantes de la reserva</h2>
          <button
            type="button"
            className="reserva-modal__close"
            onClick={onClose}
            aria-label="Cerrar"
          >
            <X size={20} strokeWidth={2.5} />
          </button>
        </header>

        <div className="reserva-modal__body">

          {/* Summary card — attendeeCount belongs to the reservation, not the roster list */}
          <div className="reserva-modal__sala-card">
            <div className="reserva-modal__sala-card-header">
              <Users size={16} />
              <span>Total de asistentes registrados: {attendeeCount}</span>
            </div>
            {rosterMismatch && (
              <div className="reserva-modal__sala-card-details reserva-students-modal__mismatch">
                <span>
                  <TriangleAlert size={14} />
                  El archivo registra {students.length} estudiante{students.length === 1 ? '' : 's'};
                  la reserva indica {attendeeCount} asistente{attendeeCount === 1 ? '' : 's'}.
                </span>
              </div>
            )}
          </div>

          {/* Roster — only students, never the attendee count */}
          {isError ? (
            <ErrorBanner
              message={error?.message || 'No se pudo cargar la lista de estudiantes.'}
              onDismiss={() => refetch()}
            />
          ) : (
            <DataTable
              columns={columns}
              rows={students}
              rowKey={(row) => `${row.firstName}|${row.lastName}|${row.email}`}
              loading={isLoading}
              loadingMessage="Cargando estudiantes…"
              emptyState={
                <EmptyState message="Esta reservación no tiene estudiantes registrados." />
              }
            />
          )}

          <footer className="reserva-modal__footer">
            <button
              type="button"
              className="reserva-modal__btn reserva-modal__btn--cancel"
              onClick={onClose}
            >
              Cerrar
            </button>
          </footer>
        </div>
      </div>
    </Modal>
  );
}
