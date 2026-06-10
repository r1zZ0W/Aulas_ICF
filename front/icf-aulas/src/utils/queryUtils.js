/**
 * Utility helpers for paginated React Query data fetching.
 * Normalizes the backend's ApiResponse envelope and provides shared constants.
 */

/** Default stale time after which React Query considers cached data outdated (5 minutes). */
export const DEFAULT_STALE_TIME = 5 * 60 * 1000;

/** Default number of items per page for paginated API calls. */
export const DEFAULT_PAGE_SIZE = 10;

/**
 * Extracts content, totalPages, and totalElements from a paginated backend response.
 * Handles multiple response shapes: { data }, { data: { content } }, or { content } directly.
 *
 * @param {object} res - Raw API response object
 * @returns {{ content: any[], totalPages: number, totalElements: number }}
 */
export function parsePageResponse(res) {
  const raw = res?.data ?? res ?? {};
  const content = Array.isArray(raw)
    ? raw
    : raw?.content ?? raw?.data?.content ?? raw?.data ?? [];
  const totalPages = raw?.totalPages ?? raw?.data?.totalPages ?? 1;
  const totalElements = raw?.totalElements ?? raw?.data?.totalElements ?? content.length;

  return { content, totalPages, totalElements };
}

/**
 * React Query queryFn wrapper that throws when the backend returns error: true.
 *
 * @param {Promise} apiCall - API call promise (e.g., classroomsApi.getAll())
 * @param {string} [errorMessage='Error loading data'] - Fallback message if the response has no message
 * @returns {Promise} Resolved API response
 */
export async function fetchWithErrorCheck(apiCall, errorMessage = "Error loading data") {
  const res = await apiCall;
  if (res?.error) {
    throw new Error(res.message ?? errorMessage);
  }
  return res;
}
