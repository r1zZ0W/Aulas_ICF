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
 * @param {object} [params={}]
 * @param {string}  [params.search]    - Free-text filter (LIKE on name/email/username/matricula).
 * @param {number}  [params.page]      - Zero-based page index.
 * @param {number}  [params.size]      - Page size (1–100).
 * @param {string}  [params.sort]      - Sort field (must be in the endpoint's allowed set).
 * @param {'asc'|'desc'} [params.direction] - Sort direction.
 * @returns {string} Query string including leading '?' or empty string.
 */
export function buildPageParams({ search, page, size, sort, direction } = {}) {
  const entries = [
    ['search',    search],
    ['page',      page],
    ['size',      size],
    ['sort',      sort],
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
  const totalPages    = inner?.totalPages    ?? 1;
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
