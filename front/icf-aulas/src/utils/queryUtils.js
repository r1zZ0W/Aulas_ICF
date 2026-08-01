/**
 * Utility helpers for paginated React Query data fetching.
 * Normalizes the backend's ApiResponse envelope and provides shared constants.
 */

/** Default stale time after which React Query considers cached data outdated (5 minutes). */
export const DEFAULT_STALE_TIME = 5 * 60 * 1000;

/** Default number of items per page for paginated API calls. */
export const DEFAULT_PAGE_SIZE = 10;

/**
 * Builds a URL query string for paginated requests against the new PagedResultDTO contract.
 * Only includes params that have a non-empty, defined value, so a no-arg call returns ''
 * (triggering the backend's "all in one page" fallback).
 *
 * @param {object}          [params={}]
 * @param {string}          [params.search]      - Free-text filter.
 * @param {string|string[]} [params.status]      - Reservation status filter (ACTIVE, CANCELLED_*).
 *                                                 Pass an array to select several statuses at once
 *                                                 (e.g. "Cancelada" = both CANCELLED_BY_USER and
 *                                                 CANCELLED_BY_ADMIN) — each element is sent as a
 *                                                 repeated `status=` param, which Spring binds to
 *                                                 a `List<ReservInstanceStatus>` on the backend.
 * @param {boolean}         [params.reassigned]  - Reassignment flag filter. IMPORTANT: `false` is a
 *                                                 valid value and MUST be sent to the backend to
 *                                                 select never-reassigned instances. Do NOT collapse
 *                                                 this to a truthy-check — that would break the
 *                                                 "Activa" partition (reassigned=false).
 * @param {string}          [params.timeframe]   - `UPCOMING`|`PAST` filter relative to today;
 *                                                 orthogonal to `status` (e.g. status=ACTIVE +
 *                                                 timeframe=PAST = "Finalizada").
 * @param {string}          [params.classroomId] - Classroom UUID filter.
 * @param {string}          [params.from]        - ISO date lower bound (yyyy-MM-dd).
 * @param {string}          [params.to]          - ISO date upper bound (yyyy-MM-dd).
 * @param {number}          [params.page]        - Zero-based page index.
 * @param {number}          [params.size]        - Page size (1–100).
 * @param {string}          [params.sort]        - Sort field (must be in the endpoint's allowed set).
 * @param {'asc'|'desc'}    [params.direction]   - Sort direction.
 * @returns {string} Query string including leading '?' or empty string.
 */
export function buildPageParams({ search, status, reassigned, timeframe, classroomId, from, to, page, size, sort, direction } = {}) {
  // `status` may be a single value (existing callers) or an array (multi-select filters like
  // "Cancelada"); normalize to a list of entries so each renders as its own `status=` param.
  const statusEntries = Array.isArray(status)
    ? status.filter((v) => v !== undefined && v !== null && v !== '').map((v) => ['status', v])
    : [['status', status]];

  const entries = [
    ['search', search],
    ...statusEntries,
    // `false` passes the filter (not undefined/null/'') — correct; do not change to truthy-check.
    ['reassigned', reassigned],
    ['timeframe', timeframe],
    ['classroomId', classroomId],
    ['from', from],
    ['to', to],
    ['page', page],
    ['size', size],
    ['sort', sort],
    ['direction', direction],
  ].filter(([, v]) => v !== undefined && v !== null && v !== '');

  if (entries.length === 0) return '';
  return '?' + entries.map(([k, v]) => `${k}=${encodeURIComponent(v)}`).join('&');
}

/**
 * Extracts items, totalPages, and totalElements from a paginated backend response.
 * Targets the current PagedResultDTO shape: { items, totalElements, totalPages, page, size }.
 *
 * @param {object} res - Raw API response object (the value returned by api.get / api.post, etc.)
 * @returns {{ items: any[], totalPages: number, totalElements: number }}
 */
export function parsePageResponse(res) {
  const raw = res?.data ?? res ?? {};
  const inner = raw?.data ?? raw;
  const items = Array.isArray(inner?.items) ? inner.items : [];
  const totalPages = inner?.totalPages ?? 1;
  const totalElements = inner?.totalElements ?? items.length;

  return { items, totalPages, totalElements };
}

/**
 * React Query queryFn wrapper that throws when the backend returns error: true.
 *
 * @param {Promise} apiCall - API call promise (e.g., classroomsApi.getAll())
 * @param {string} [errorMessage='Error loading data'] - Fallback message if the response has no message
 * @returns {Promise} Resolved API response
 */
export async function fetchWithErrorCheck(apiCall, errorMessage = 'Error loading data') {
  const res = await apiCall;
  if (res?.error) {
    throw new Error(res.message ?? errorMessage);
  }
  return res;
}
