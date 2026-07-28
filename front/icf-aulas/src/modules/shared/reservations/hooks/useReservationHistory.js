/**
 * @fileoverview Custom hook that encapsulates server-state logic for the
 * Historial de Reservas page. All filtering is delegated to the server; this
 * hook is stateless with respect to filters — it receives resolved params and
 * fires the appropriate queries.
 *
 * Query key structure:
 *   Admin "Todas":  ['reservations','history','all',  filterParams]
 *   "Mis Reservas": ['reservations','history','user', userUuid, filterParams]
 *
 * These keys are invalidated by cancel/reassign mutations in ReservationContext
 * so the badge column updates in real time without manual state management.
 */
import { useState } from 'react';
import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { getReservations, getReservationsByUser } from '../../../../api/reservations.js';
import { parsePageResponse } from '../../../../utils/queryUtils.js';
import { ROLES } from '../../../../utils/roles.js';

/**
 * @param {object}         params
 * @param {object}         params.user               - Authenticated user from useAuth() ({ uuid, role }).
 * @param {number}         [params.page=0]            - Zero-based current page (URL-synced).
 * @param {number}         [params.size=10]           - Page size.
 * @param {string}         [params.search='']         - Server-side full-text search (≥3 chars gate is
 *                                                       enforced upstream in HistoryPage, not here).
 * @param {string}         [params.status='']         - Reservation status filter (ACTIVE, CANCELLED_*).
 * @param {boolean|undefined} [params.reassigned]     - Reassignment flag filter:
 *                                                       true  = only admin-reassigned instances,
 *                                                       false = only never-reassigned instances,
 *                                                       undefined = no restriction (omits param).
 *                                                       No default so undefined propagates cleanly.
 * @param {string}         [params.classroomId='']   - Classroom UUID filter.
 * @param {string}         [params.from]              - ISO date lower bound (yyyy-MM-dd).
 * @param {string}         [params.to]                - ISO date upper bound (yyyy-MM-dd).
 * @param {string}         [params.sort='date']       - Sort field (createdAt | date | status).
 * @param {'asc'|'desc'}   [params.direction='desc']  - Sort direction.
 *
 * @returns {{
 *   items:         object[],
 *   totalElements: number,
 *   totalPages:    number,
 *   loading:       boolean,
 *   tab:           'all'|'mine',
 *   setTab:        (tab: 'all'|'mine') => void,
 *   isAdmin:       boolean,
 * }}
 */
export function useReservationHistory({
  user,
  page = 0,
  size = 10,
  search = '',
  status = '',
  reassigned,
  classroomId = '',
  from,
  to,
  sort = 'date',
  direction = 'desc',
}) {
  const isAdmin = user?.role === ROLES.ADMIN;
  const [tab, setTab] = useState('all');

  const effectiveUuid = user?.uuid;
  const queryAll = isAdmin && tab === 'all';

  const filterParams = {
    page,
    size,
    search:      search      || undefined,
    status:      status      || undefined,
    // `reassigned` has no || undefined guard: `false` is a valid filter value that must be sent.
    // `undefined` (no restriction) is left as-is so buildPageParams omits the param entirely.
    reassigned,
    classroomId: classroomId || undefined,
    from,
    to,
    sort,
    direction,
  };

  const { data: allData, isLoading: allLoading, isError: allError, refetch: refetchAll } = useQuery({
    queryKey: ['reservations', 'history', 'all', filterParams],
    queryFn:  () => getReservations(filterParams),
    placeholderData: keepPreviousData,
    enabled: queryAll,
  });

  const { data: userHistData, isLoading: userHistLoading, isError: userHistError, refetch: refetchUserHist } = useQuery({
    queryKey: ['reservations', 'history', 'user', effectiveUuid, filterParams],
    queryFn:  () => getReservationsByUser(effectiveUuid, filterParams),
    placeholderData: keepPreviousData,
    enabled: !!effectiveUuid && !queryAll,
  });

  const rawData = queryAll ? allData : userHistData;
  const loading  = queryAll ? allLoading : userHistLoading;
  const isError  = queryAll ? allError : userHistError;
  const refetch  = queryAll ? refetchAll : refetchUserHist;

  const { items, totalPages, totalElements } = parsePageResponse(rawData);

  return { items, totalElements, totalPages, loading, isError, refetch, tab, setTab, isAdmin };
}
