/**
 * @fileoverview Custom hook that encapsulates server-state logic for the
 * Historial de Reservas page: paginated reservation instance lists, tab
 * state (admin only), and a client-side text filter applied to the current page.
 *
 * Query key structure (under the ['reservations','history'] segment):
 *   Admin "Todas":    ['reservations', 'history', 'all',  { page, size, sort, direction }]
 *   "Mis Reservas":   ['reservations', 'history', 'user', userUuid, { page, size, sort, direction }]
 *
 * These keys are invalidated by the cancel/reassign mutations in ReservationContext
 * so the badge column updates in real time without manual state management.
 *
 * NOTE on server-side filters: the backend endpoints do not yet accept ?search=,
 * ?status=, ?classroomId=, ?from=, ?to= parameters. Until they do, filtering is
 * applied client-side over the current page only. See back/doc/historial-reservas-brechas.md §1.
 */
import { useState } from 'react';
import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { getReservations, getReservationsByUser } from '../../../../api/reservations.js';
import { parsePageResponse } from '../../../../utils/queryUtils.js';
import { ROLES } from '../../../../utils/roles.js';

// ── Constants ─────────────────────────────────────────────────────────────────

const SORT     = 'date';
const DIRECTION = 'desc';

// ── Hook ──────────────────────────────────────────────────────────────────────

/**
 * @param {object} params
 * @param {object} params.user      - Authenticated user from useAuth() ({ uuid, role }).
 * @param {number} params.page      - Zero-based current page (URL-synced via usePagination).
 * @param {number} params.size      - Page size.
 * @param {string} params.search    - Debounced search term (client-side filter on current page).
 *
 * @returns {{
 *   items:         object[],
 *   filteredItems: object[],
 *   totalElements: number,
 *   totalPages:    number,
 *   loading:       boolean,
 *   tab:           'all'|'mine',
 *   setTab:        (tab: 'all'|'mine') => void,
 *   isAdmin:       boolean,
 * }}
 */
export function useReservationHistory({ user, page = 0, size = 10, search = '' }) {
  const isAdmin = user?.role === ROLES.ADMIN;

  // ── Tab state (admin only — 'all' | 'mine') ──────────────────────────────
  const [tab, setTab] = useState('all');

  // The effective userUuid to query: 'mine' tab or maestro always uses own uuid
  const effectiveUuid = user?.uuid;
  const queryAll  = isAdmin && tab === 'all';

  // ── All reservations (admin "Todas" tab) ──────────────────────────────────
  const {
    data: allData,
    isLoading: allLoading,
  } = useQuery({
    queryKey: ['reservations', 'history', 'all', { page, size, sort: SORT, direction: DIRECTION }],
    queryFn:  () => getReservations({ page, size, sort: SORT, direction: DIRECTION }),
    placeholderData: keepPreviousData,
    enabled: queryAll,
  });

  // ── User reservations ("Mis Reservas" / Maestro) ──────────────────────────
  const {
    data: userHistData,
    isLoading: userHistLoading,
  } = useQuery({
    queryKey: ['reservations', 'history', 'user', effectiveUuid, { page, size, sort: SORT, direction: DIRECTION }],
    queryFn:  () => getReservationsByUser(effectiveUuid, { page, size, sort: SORT, direction: DIRECTION }),
    placeholderData: keepPreviousData,
    enabled: !!effectiveUuid && !queryAll,
  });

  // ── Resolve active data source ─────────────────────────────────────────────
  const rawData = queryAll ? allData : userHistData;
  const loading = queryAll ? allLoading : userHistLoading;

  const { items, totalPages, totalElements } = parsePageResponse(rawData);

  // ── Client-side filter (current page only) ────────────────────────────────
  // TODO(brecha-1): replace with server-side ?search= once the backend supports
  // JPA Specifications on the instances endpoints. See back/doc/historial-reservas-brechas.md §1.
  const term = search.trim().toLowerCase();
  const filteredItems = term
    ? items.filter(r =>
        r.classroomName?.toLowerCase().includes(term) ||
        r.userFullName?.toLowerCase().includes(term)
      )
    : items;

  return {
    items,
    filteredItems,
    totalElements,
    totalPages,
    loading,
    tab,
    setTab,
    isAdmin,
  };
}
