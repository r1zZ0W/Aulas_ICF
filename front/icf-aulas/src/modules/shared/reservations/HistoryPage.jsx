import { useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { History, ArrowUpDown, ArrowUp, ArrowDown, X } from 'lucide-react';

import { useAuth } from '../../../context/AuthContext';
import { useReservation } from '../../../context/ReservationContext';
import { useReservationHistory } from './hooks/useReservationHistory';
import { optionsFor, toQueryParams, normalizeKey } from './statusFilter';
import { usePagination } from '../../../hooks/usePagination';
import { useTableSort } from '../../../hooks/useTableSort';
import { useUrlFilters } from '../../../hooks/useUrlFilters';
import { DEFAULT_PAGE_SIZE } from '../../../utils/queryUtils';
import { fmtTime, reservationBadge, slotsToRange } from '../../../utils/reservations';

import Buscador from '../../../components/Buscador/Buscador';
import DataTable from '../../../components/DataTable/DataTable';
import Badge from '../../../components/Badge/Badge';
import EmptyState from '../../../components/EmptyState/EmptyState';
import ErrorBanner from '../../../components/ErrorBanner/ErrorBanner';
import Pagination from '../../../components/Pagination/Pagination';
import Select from '../../../components/Select/Select';

import './HistoryPage.css';

// ── Constants ─────────────────────────────────────────────────────────────────

const DATE_REGEX = /^\d{4}-\d{2}-\d{2}$/;
const ALLOWED_SORTS = ['date', 'status', 'createdAt'];

// ── Subcomponents ─────────────────────────────────────────────────────────────

/**
 * Formats a reservation's date and time slot range as a readable string.
 * e.g. "Lun 23 Jun · 9:00 – 11:00"
 */
function formatDatetime(reservation) {
  const { date, timeSlots } = reservation;
  if (!date) return '—';
  const [y, m, d] = date.split('-').map(Number);
  const dateObj = new Date(y, m - 1, d);
  const dayLabel = dateObj.toLocaleDateString('es-MX', { weekday: 'short', day: 'numeric', month: 'short' });
  const { start, end } = slotsToRange(date, timeSlots ?? []);
  if (!start) return dayLabel;
  return `${dayLabel} · ${fmtTime(start)} – ${fmtTime(end)}`;
}

/**
 * Clickable table header that toggles sort direction.
 * Shows an arrow indicating the current sort state.
 */
function SortHeader({ field, label, sort, direction, onToggle }) {
  const isActive = sort === field;
  return (
    <button
      type="button"
      className={`history-page__sort-header${isActive ? ' history-page__sort-header--active' : ''}`}
      onClick={() => onToggle(field)}
      title={`Ordenar por ${label}`}
    >
      {label}
      <span className="history-page__sort-arrow" aria-hidden="true">
        {isActive
          ? (direction === 'asc' ? <ArrowUp size={12} /> : <ArrowDown size={12} />)
          : <ArrowUpDown size={12} />}
      </span>
    </button>
  );
}

// ── Component ─────────────────────────────────────────────────────────────────

/**
 * Role-aware reservation history page with server-side filters.
 *
 * All list state (search, page, sort, direction, status, classroomId, from, to)
 * lives in the URL as the single source of truth — copying the URL reproduces
 * the exact view in another tab. The `status` param holds a *selection key*
 * (see `./statusFilter.js`), translated into the real `status`/`reassigned`/`timeframe`
 * API params in one place so the dropdown label and the fetched data can never disagree.
 *
 * Maestro view: own reservations only, no Organizador column, no tabs.
 * Admin view: tabs "Todas las Reservas" / "Mis Reservas"; Organizador column on "Todas".
 */
export default function HistoryPage() {
  const { user } = useAuth();
  const { rooms, openInfoModal } = useReservation();

  // URL-synced state — all three hooks write to useSearchParams
  const { searchInput, setSearchInput, search, page, setPage } = usePagination({ debounce: 300, minSearchLength: 3 });
  const { values: filterValues, setFilter, resetFilters } = useUrlFilters(['status', 'classroomId', 'from', 'to']);
  const [, setParams] = useSearchParams();

  // ── Render-phase sanitization ───────────────────────────────────────────────
  // Must happen here (not in a useEffect) so that React Query always receives valid
  // params from the very first render cycle — an effect fires after render and would
  // let a bad/unknown status key reach the backend for one render (→ 400).
  const statusKey = normalizeKey(filterValues.status, user?.role);
  const { status: apiStatus, reassigned: apiReassigned, timeframe: apiTimeframe } = toQueryParams(statusKey);

  const safeClassroomId = filterValues.classroomId || '';
  const safeFrom = DATE_REGEX.test(filterValues.from) ? filterValues.from : '';
  const safeTo = DATE_REGEX.test(filterValues.to) ? filterValues.to : '';

  // ── Cosmetic URL cleanup (useEffect) ────────────────────────────────────────
  // The render-phase block above already made the fetch correct; this effect only
  // rewrites the address bar so bookmarks / copy-paste produce canonical URLs, and
  // sweeps the legacy `reassigned` param (pre-dating the single-key `status` contract).
  useEffect(() => {
    const rawStatus = filterValues.status;
    const legacyReassigned = new URLSearchParams(window.location.search).get('reassigned');
    if (!rawStatus && legacyReassigned === null) return;

    // A maestro's old bookmark for "Cancelada" used the raw backend status
    // (`CANCELLED_BY_USER`) directly — that literal is no longer a maestro-facing key
    // (their "Cancelada" now groups both actors under `CANCELLED`). Upgrade it instead
    // of just dropping the filter, so the bookmark keeps working.
    const isLegacyCancelledLiteral =
      (rawStatus === 'CANCELLED_BY_USER' || rawStatus === 'CANCELLED_BY_ADMIN')
      && normalizeKey(rawStatus, user?.role) === '';

    let canonicalKey = statusKey;
    if (rawStatus === 'ACTIVE' && legacyReassigned === 'true') {
      canonicalKey = 'REASSIGNED'; // old two-param combo for "Reasignada"
    } else if (isLegacyCancelledLiteral) {
      canonicalKey = 'CANCELLED';
    }

    if (rawStatus !== canonicalKey || legacyReassigned !== null) {
      setParams(prev => {
        const next = new URLSearchParams(prev);
        if (canonicalKey) next.set('status', canonicalKey); else next.delete('status');
        next.delete('reassigned');
        return next;
      }, { replace: true });
    }
  }, [filterValues.status, statusKey, user?.role, setParams]);

  function applyStatusSelection(value) {
    setParams(prev => {
      const next = new URLSearchParams(prev);
      next.delete('page'); // Reset pagination whenever the status filter changes.
      if (value) next.set('status', value); else next.delete('status');
      next.delete('reassigned'); // no longer a distinct URL param — swept defensively
      return next;
    }, { replace: true });
  }

  // Próximas (UPCOMING) read best nearest-first; Finalizadas/Canceladas read best most-recent-first.
  // An explicit `direction` in the URL (from clicking a column header) still wins — see useTableSort.
  const defaultDirection = apiTimeframe === 'UPCOMING' ? 'asc' : 'desc';
  const { sort, direction, toggleSort } = useTableSort({ defaultSort: 'date', defaultDirection, allowed: ALLOWED_SORTS });

  // Render-layer short-circuits (plan items #7 and #8):
  //  • isIncompleteSearch: hook is blind to local input — force items=[] in render
  //    instead of enabled:false (which wouldn't clear React Query cache).
  //  • !dateRangeValid: force items=[] AND exclude from/to from the query, avoiding
  //    the "paradox of omission" (showing all data while the error is red).
  const isIncompleteSearch = searchInput.trim().length > 0 && searchInput.trim().length < 3;
  const dateRangeValid = (!safeFrom || !safeTo) || (safeFrom <= safeTo);
  const blocked = isIncompleteSearch || !dateRangeValid;

  const { items: rawItems, totalElements, totalPages, loading, isError, refetch, tab, setTab, isAdmin } = useReservationHistory({
    user,
    page,
    size: DEFAULT_PAGE_SIZE,
    search,
    status: apiStatus,
    reassigned: apiReassigned,
    timeframe: apiTimeframe,
    classroomId: safeClassroomId,
    from: (dateRangeValid && safeFrom) ? safeFrom : undefined,
    to: (dateRangeValid && safeTo) ? safeTo : undefined,
    sort,
    direction,
  });

  const items = blocked ? [] : rawItems;
  const showOrganizer = isAdmin && tab === 'all';
  const hasFilters = !!(search || statusKey || safeClassroomId || safeFrom || safeTo);

  // ── Room options for the filter dropdown ──────────────────────────────────
  const roomOptions = [
    { value: '', label: 'Todas las aulas' },
    ...rooms.map(r => ({ value: r.uuid, label: r.label })),
  ];

  // ── Column definitions ────────────────────────────────────────────────────
  const columns = [
    {
      key: 'detalle',
      header: 'Detalle',
      width: '28%',
      render: (row) => (
        <div className="history-page__detail-cell">
          <span className="history-page__detail-name">{(row.title ?? row.classroomName) || '—'}</span>
          {row.title && <span className="history-page__detail-classroom">{row.classroomName}</span>}
        </div>
      ),
    },
    {
      key: 'fechaHora',
      header: <SortHeader field="date" label="Fecha y Hora" sort={sort} direction={direction} onToggle={toggleSort} />,
      width: '30%',
      render: (row) => <span className="history-page__datetime">{formatDatetime(row)}</span>,
    },
    ...(showOrganizer ? [{
      key: 'organizador',
      header: 'Organizador',
      width: '22%',
      render: (row) => <span className="history-page__organizer">{row.userFullName || '—'}</span>,
    }] : []),
    {
      key: 'estado',
      header: <SortHeader field="status" label="Estado" sort={sort} direction={direction} onToggle={toggleSort} />,
      width: showOrganizer ? '20%' : '32%',
      render: (row) => {
        const { variant, label } = reservationBadge(row);
        return <Badge variant={variant}>{label}</Badge>;
      },
    },
  ];

  // ── Derived UI ────────────────────────────────────────────────────────────
  const pageTitle = isAdmin ? 'Historial General de Reservas' : 'Historial de Reservas';

  // Single setParams call (via resetFilters) + local state clear — atomic, one history entry
  const handleReset = () => {
    resetFilters(['search', 'sort', 'direction', 'reassigned']);
    setSearchInput('');
  };

  return (
    <div className="history-page">

      {/* Header */}
      <div className="history-page__header">
        <div className="history-page__header-text">
          <h1 className="history-page__title">
            <History size={22} className="history-page__title-icon" />
            {pageTitle}
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
        {isError && (
          <ErrorBanner
            message="No se pudo cargar el historial de reservas."
            onDismiss={() => refetch()}
          />
        )}

        {/* Toolbar row 1: text search + dropdowns + reset */}
        <div className="history-page__toolbar">
          <Buscador
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            placeholder="Buscar por aula o maestro… (mín. 3 caracteres)"
            style={{ flex: '1 1 200px', maxWidth: 440 }}
          />
          <Select
            value={statusKey}
            onChange={applyStatusSelection}
            options={optionsFor(user?.role)}
            placeholder="Estado"
            size="sm"
            className="history-page__filter-select"
          />
          <Select
            value={safeClassroomId}
            onChange={(v) => setFilter('classroomId', v)}
            options={roomOptions}
            placeholder="Aula"
            size="sm"
            className="history-page__filter-select"
          />
          {hasFilters && (
            <button
              type="button"
              className="history-page__reset-btn"
              onClick={handleReset}
              title="Limpiar todos los filtros y el orden"
            >
              <X size={14} />
              Limpiar
            </button>
          )}
        </div>

        {/* Toolbar row 2: date range — each extreme optional and independent */}
        <div className="history-page__date-toolbar">
          <fieldset className="history-page__date-range">
            <legend className="history-page__date-legend">Rango de fechas</legend>
            <div className="history-page__date-inputs">
              <div className="history-page__date-field">
                <label className="history-page__date-label" htmlFor="filter-from">Desde</label>
                <input
                  id="filter-from"
                  type="date"
                  className={`history-page__date-input${!dateRangeValid ? ' history-page__date-input--error' : ''}`}
                  value={safeFrom}
                  max={safeTo || undefined}
                  onChange={(e) => setFilter('from', e.target.value)}
                />
              </div>
              <div className="history-page__date-field">
                <label className="history-page__date-label" htmlFor="filter-to">Hasta</label>
                <input
                  id="filter-to"
                  type="date"
                  className={`history-page__date-input${!dateRangeValid ? ' history-page__date-input--error' : ''}`}
                  value={safeTo}
                  min={safeFrom || undefined}
                  onChange={(e) => setFilter('to', e.target.value)}
                />
              </div>
              {!dateRangeValid && (
                <span className="history-page__date-error" role="alert">
                  "Hasta" no puede ser anterior a "Desde"
                </span>
              )}
            </div>
            <p className="history-page__date-hint">Puedes dejar un extremo abierto</p>
          </fieldset>
        </div>

        {/* Table — clicking a row opens the shared info/reassign modal */}
        <DataTable
          columns={columns}
          rows={items}
          rowKey={(row) => row.uuid}
          loading={loading}
          loadingMessage="Cargando historial…"
          onRowClick={openInfoModal}
          emptyState={
            isIncompleteSearch
              ? (
                <div className="history-page__search-hint">
                  Escribe al menos 3 caracteres para buscar.
                </div>
              )
              : (
                <EmptyState
                  hasSearch={hasFilters}
                  message="Aún no hay reservas registradas."
                  searchMessage="No se encontraron reservas que coincidan con los filtros."
                />
              )
          }
        />

        {/* Pagination — hidden while blocked (incomplete search / invalid date range) */}
        {!loading && !blocked && (
          <Pagination
            page={page}
            totalPages={totalPages}
            onPageChange={setPage}
            pageSize={items.length}
            total={totalElements}
            noun="reserva"
            searchActive={hasFilters}
          />
        )}
      </div>
    </div>
  );
}
