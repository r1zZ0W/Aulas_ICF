import { History } from 'lucide-react';

import { useAuth } from '../../../context/AuthContext';
import { useReservation } from '../../../context/ReservationContext';
import { useReservationHistory } from './hooks/useReservationHistory';
import { usePagination } from '../../../hooks/usePagination';
import { DEFAULT_PAGE_SIZE } from '../../../utils/queryUtils';
import { getShortId, fmtTime, reservationBadge, slotsToRange } from '../../../utils/reservations';

import Buscador from '../../../components/Buscador/Buscador';
import DataTable from '../../../components/DataTable/DataTable';
import Badge from '../../../components/Badge/Badge';
import EmptyState from '../../../components/EmptyState/EmptyState';
import Pagination from '../../../components/Pagination/Pagination';
import Alert from '../../../components/Alert/Alert';

import './HistoryPage.css';

// ── History date+time cell ────────────────────────────────────────────────────

/**
 * Formats a reservation's date and time slot range as a readable string.
 * e.g. "Lun 23 Jun · 9:00 – 11:00"
 *
 * @param {object} reservation - ReservInstanceResponseDTO
 * @returns {string}
 */
function formatDatetime(reservation) {
  const { date, timeSlots } = reservation;
  if (!date) return '—';

  // Parse YYYY-MM-DD in local time to avoid UTC-shift
  const [y, m, d] = date.split('-').map(Number);
  const dateObj = new Date(y, m - 1, d);
  const dayLabel = dateObj.toLocaleDateString('es-MX', { weekday: 'short', day: 'numeric', month: 'short' });

  const { start, end } = slotsToRange(date, timeSlots ?? []);
  if (!start) return dayLabel;

  return `${dayLabel} · ${fmtTime(start)} – ${fmtTime(end)}`;
}

// ── Component ─────────────────────────────────────────────────────────────────

/**
 * Role-aware reservation history page.
 *
 * Maestro view ("Historial de Reservas"):
 *   - Shows only the logged-in teacher's own reservations.
 *   - No Organizador column, no tabs.
 *
 * Admin view ("Historial General de Reservas"):
 *   - Tabs: "Todas las Reservas" / "Mis Reservas".
 *   - "Todas" shows the Organizador (userFullName) column.
 *   - "Mis Reservas" hides Organizador (same layout as the Maestro view).
 *
 * Clicking a row opens the shared ReservaInfoModal (and optionally ReasignarModal
 * for admins). Both modals are already mounted in PrivateLayout and listen to
 * openInfoModal / openReschedule from ReservationContext — no duplication needed.
 *
 * Badge status updates in real time via React Query cache invalidation:
 * cancel/reassign mutations invalidate ['reservations','history'], which covers
 * this page's query keys, so the table refetches automatically.
 *
 * Search filter: client-side over the current page only (server-side filters not
 * yet implemented). See back/doc/historial-reservas-brechas.md §1 (Brecha 1).
 */
export default function HistoryPage() {
  const { user } = useAuth();
  const { openInfoModal } = useReservation();

  // URL-synced pagination + debounced search input
  const { searchInput, setSearchInput, search, page, setPage } = usePagination({ debounce: 300 });

  const {
    filteredItems,
    totalElements,
    totalPages,
    loading,
    tab,
    setTab,
    isAdmin,
  } = useReservationHistory({
    user,
    page,
    size: DEFAULT_PAGE_SIZE,
    search,
  });

  // Show Organizador only when admin is on the "Todas" tab
  const showOrganizador = isAdmin && tab === 'all';

  // ── Column definitions ───────────────────────────────────────────────────────
  const columns = [
    {
      key: 'detalle',
      header: 'Detalle',
      width: '28%',
      render: (row) => (
        <div className="history-page__detail-cell">
          <span className="history-page__detail-name">{row.classroomName || '—'}</span>
          <span className="history-page__detail-id">{getShortId(row.uuid)}</span>
        </div>
      ),
    },
    {
      key: 'fechaHora',
      header: 'Fecha y Hora',
      width: '30%',
      render: (row) => (
        <span className="history-page__datetime">{formatDatetime(row)}</span>
      ),
    },
    // Organizador: only visible to admin on the "Todas" tab
    ...(showOrganizador
      ? [{
          key: 'organizador',
          header: 'Organizador',
          width: '22%',
          render: (row) => (
            <span className="history-page__organizer">{row.userFullName || '—'}</span>
          ),
        }]
      : []),
    {
      key: 'estado',
      header: 'Estado',
      width: showOrganizador ? '20%' : '32%',
      render: (row) => {
        const { variant, label } = reservationBadge(row);
        return <Badge variant={variant}>{label}</Badge>;
      },
    },
  ];

  // ── Derived UI state ─────────────────────────────────────────────────────────
  const title    = isAdmin ? 'Historial General de Reservas' : 'Historial de Reservas';
  const hasSearch = !!search.trim();

  return (
    <div className="history-page">

      {/* Header */}
      <div className="history-page__header">
        <div className="history-page__header-text">
          <h1 className="history-page__title">
            <History size={22} className="history-page__title-icon" />
            {title}
          </h1>
          <p className="history-page__subtitle">
            {isAdmin
              ? 'Consulta y administra las reservas del sistema'
              : 'Revisa el historial de tus reservas de aula'}
          </p>
        </div>
      </div>

      {/* Admin tabs */}
      {isAdmin && (
        <div className="history-page__tabs" role="tablist">
          <button
            type="button"
            role="tab"
            aria-selected={tab === 'all'}
            className={`history-page__tab${tab === 'all' ? ' history-page__tab--active' : ''}`}
            onClick={() => { setTab('all'); setPage(0); }}
          >
            Todas las Reservas
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={tab === 'mine'}
            className={`history-page__tab${tab === 'mine' ? ' history-page__tab--active' : ''}`}
            onClick={() => { setTab('mine'); setPage(0); }}
          >
            Mis Reservas
          </button>
        </div>
      )}

      {/* Table card */}
      <div className="history-page__table-card">

        {/* Toolbar */}
        <div className="history-page__toolbar">
          <Buscador
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            placeholder="Buscar por aula o maestro…"
            style={{ maxWidth: 400 }}
          />
          {/*
            TODO(brecha-1): Add ?status=, ?classroomId=, ?from=/?to= filter dropdowns
            once the backend implements JPA Specifications on the instances endpoints.
            See back/doc/historial-reservas-brechas.md §1 (Brecha 1).
          */}
        </div>

        {/* Client-side search scope warning */}
        {hasSearch && (
          <Alert variant="warning" className="history-page__search-alert">
            La búsqueda solo aplica a los registros visibles en esta página. Para buscar en todo el
            historial, navega a la página donde se encuentre la reserva.
          </Alert>
        )}

        {/* Table — clicking a row opens the shared info/reassign modal */}
        <DataTable
          columns={columns}
          rows={filteredItems}
          rowKey={(row) => row.uuid}
          loading={loading}
          loadingMessage="Cargando historial…"
          onRowClick={openInfoModal}
          emptyState={
            <EmptyState
              hasSearch={hasSearch}
              message="Aún no hay reservas registradas."
              searchMessage="No se encontraron reservas que coincidan con la búsqueda."
            />
          }
        />

        {/* Pagination */}
        {!loading && (
          <Pagination
            page={page}
            totalPages={totalPages}
            onPageChange={setPage}
            showing={filteredItems.length}
            total={totalElements}
            noun="reserva"
            searchActive={hasSearch}
          />
        )}
      </div>
    </div>
  );
}
