import { useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { History, ArrowUpDown, ArrowUp, ArrowDown, X } from 'lucide-react';

import { useAuth } from '../../../context/AuthContext';
import { useReservation } from '../../../context/ReservationContext';
import { useReservationHistory } from './hooks/useReservationHistory';
import { usePagination } from '../../../hooks/usePagination';
import { useTableSort } from '../../../hooks/useTableSort';
import { useUrlFilters } from '../../../hooks/useUrlFilters';
import { DEFAULT_PAGE_SIZE } from '../../../utils/queryUtils';
import { fmtTime, reservationBadge, slotsToRange } from '../../../utils/reservations';

import Buscador from '../../../components/Buscador/Buscador';
import DataTable from '../../../components/DataTable/DataTable';
import Badge from '../../../components/Badge/Badge';
import EmptyState from '../../../components/EmptyState/EmptyState';
import Pagination from '../../../components/Pagination/Pagination';
import Select from '../../../components/Select/Select';

import './HistoryPage.css';

// ── Constants ─────────────────────────────────────────────────────────────────

const VALID_STATUSES = ['ACTIVE', 'CANCELLED_BY_USER', 'CANCELLED_BY_ADMIN'];
const DATE_REGEX = /^\d{4}-\d{2}-\d{2}$/;
const ALLOWED_SORTS = ['date', 'status', 'createdAt'];

const STATUS_OPTIONS_ADMIN = [
  { value: '',                   label: 'Todos los estados'      },
  { value: 'ACTIVE',             label: 'Activa'                 },
  { value: 'REASSIGNED',         label: 'Reasignada'             },
  { value: 'CANCELLED_BY_USER',  label: 'Cancelada por maestro'  },
  { value: 'CANCELLED_BY_ADMIN', label: 'Cancelada por admin'    },
];

const STATUS_OPTIONS_TEACHER = [
  { value: '',           label: 'Todos los estados' },
  { value: 'ACTIVE',     label: 'Activa'            },
  { value: 'REASSIGNED', label: 'Reasignada'        },
  { value: 'CANCELLED_BY_USER', label: 'Cancelada'  },
];

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
 * the exact view in another tab.
 *
 * Maestro view: own reservations only, no Organizador column, no tabs.
 * Admin view: tabs "Todas las Reservas" / "Mis Reservas"; Organizador column on "Todas".
 */
export default function HistoryPage() {
  const { user } = useAuth();
  const { rooms, openInfoModal } = useReservation();

  // URL-synced state — all three hooks write to useSearchParams
  const { searchInput, setSearchInput, search, page, setPage } = usePagination({ debounce: 300, minSearchLength: 3 });
  const { sort, direction, toggleSort } = useTableSort({ defaultSort: 'date', defaultDirection: 'desc', allowed: ALLOWED_SORTS });
  const { values: filterValues, setFilter, resetFilters } = useUrlFilters(['status', 'reassigned', 'classroomId', 'from', 'to']);
  const [, setParams] = useSearchParams();

  // ── Render-phase sanitization ───────────────────────────────────────────────
  // Must happen here (not in a useEffect) so that React Query always receives valid
  // params from the very first render cycle — an effect fires after render and would
  // let a bad status=REASSIGNED or status=GARBAGE request reach the backend (→ 400).

  // 'REASSIGNED' is a virtual UI alias; map it to real params in memory.
  const isVirtualReassigned = filterValues.status === 'REASSIGNED';

  const safeStatus = isVirtualReassigned
    ? 'ACTIVE'
    : (VALID_STATUSES.includes(filterValues.status) ? filterValues.status : '');

  // ABSOLUTE RULE: `reassigned` is an EXCLUSIVE sub-partition of ACTIVE.
  // Derive from safeStatus (not filterValues.reassigned in isolation) so that
  // ?status=CANCELLED_BY_USER&reassigned=true or a bare ?reassigned=true cannot
  // inject the flag into a non-ACTIVE query (URL injection defense).
  //   • safeStatus !== 'ACTIVE' → undefined (buildPageParams strips the param)
  //   • ACTIVE + reassigned=true (or virtual alias) → true
  //   • ACTIVE, any other case  → false (clean "Activa" partition, anti-leak)
  const safeReassigned = safeStatus === 'ACTIVE'
    ? (filterValues.reassigned === 'true' || isVirtualReassigned ? true : false)
    : undefined;

  const safeClassroomId = filterValues.classroomId || '';
  const safeFrom = DATE_REGEX.test(filterValues.from) ? filterValues.from : '';
  const safeTo = DATE_REGEX.test(filterValues.to) ? filterValues.to : '';

  // ── Cosmetic URL cleanup (useEffect) ────────────────────────────────────────
  // The render-phase block above already made the fetch correct; this effect only
  // rewrites the address bar so bookmarks / copy-paste produce canonical URLs.
  // It also sweeps unknown garbage statuses so the URL doesn't stay dirty silently.
  useEffect(() => {
    const raw = filterValues.status;
    if (!raw) return;
    if (raw === 'REASSIGNED') {
      setParams(prev => {
        const next = new URLSearchParams(prev);
        next.set('status', 'ACTIVE');
        next.set('reassigned', 'true');
        return next;
      }, { replace: true });
    } else if (!VALID_STATUSES.includes(raw)) {
      // Unknown value → strip it (and any orphan reassigned flag) from the URL.
      setParams(prev => {
        const next = new URLSearchParams(prev);
        next.delete('status');
        next.delete('reassigned');
        return next;
      }, { replace: true });
    }
  }, [filterValues.status, setParams]); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Atomic setter for the (status, reassigned) pair ─────────────────────────
  // useUrlFilters.setFilter deletes any key whose value is falsy, so it cannot
  // persist `reassigned=false` in isolation. Both params must be written in one
  // setParams call to produce a single browser-history entry.
  function applyStatusSelection(value) {
    setParams(prev => {
      const next = new URLSearchParams(prev);
      next.delete('page'); // Reset pagination whenever the status filter changes.
      if (value === 'REASSIGNED') {
        next.set('status', 'ACTIVE');
        next.set('reassigned', 'true');
      } else if (value === 'ACTIVE') {
        next.set('status', 'ACTIVE');
        next.set('reassigned', 'false');
      } else if (value) {
        // CANCELLED_BY_USER / CANCELLED_BY_ADMIN — no reassigned constraint.
        next.set('status', value);
        next.delete('reassigned');
      } else {
        // '' → "Todos los estados" — clear both params.
        next.delete('status');
        next.delete('reassigned');
      }
      return next;
    }, { replace: true });
  }

  // Derive the Select's displayed value from the sanitized state so the label
  // and the data always agree — never read the raw URL value here.
  const statusSelectValue = safeStatus === 'ACTIVE'
    ? (safeReassigned ? 'REASSIGNED' : 'ACTIVE')
    : (safeStatus || '');

  // Render-layer short-circuits (plan items #7 and #8):
  //  • isIncompleteSearch: hook is blind to local input — force items=[] in render
  //    instead of enabled:false (which wouldn't clear React Query cache).
  //  • !dateRangeValid: force items=[] AND exclude from/to from the query, avoiding
  //    the "paradox of omission" (showing all data while the error is red).
  const isIncompleteSearch = searchInput.trim().length > 0 && searchInput.trim().length < 3;
  const dateRangeValid = (!safeFrom || !safeTo) || (safeFrom <= safeTo);
  const blocked = isIncompleteSearch || !dateRangeValid;

  const { items: rawItems, totalElements, totalPages, loading, tab, setTab, isAdmin } = useReservationHistory({
    user,
    page,
    size: DEFAULT_PAGE_SIZE,
    search,
    status: safeStatus,
    reassigned: safeReassigned,
    classroomId: safeClassroomId,
    from: (dateRangeValid && safeFrom) ? safeFrom : undefined,
    to: (dateRangeValid && safeTo) ? safeTo : undefined,
    sort,
    direction,
  });

  const items = blocked ? [] : rawItems;
  const showOrganizer = isAdmin && tab === 'all';
  const hasFilters = !!(search || safeStatus || safeReassigned !== undefined || safeClassroomId || safeFrom || safeTo);

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
    resetFilters(['search', 'sort', 'direction']);
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

        {/* Toolbar row 1: text search + dropdowns + reset */}
        <div className="history-page__toolbar">
          <Buscador
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            placeholder="Buscar por aula o maestro… (mín. 3 caracteres)"
            style={{ flex: '1 1 200px', maxWidth: 440 }}
          />
          <Select
            value={statusSelectValue}
            onChange={applyStatusSelection}
            options={isAdmin ? STATUS_OPTIONS_ADMIN : STATUS_OPTIONS_TEACHER}
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
