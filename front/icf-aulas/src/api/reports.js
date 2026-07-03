/**
 * @fileoverview API client for the Reportes y Estadísticas module.
 *
 * `getReservationStatistics` calls the real backend endpoint:
 *   GET /api/v1/reports/statistics?scope=MENSUAL|SEMESTRAL[&anchor=...]
 *
 * The response is an `ApiResponse<ReservationStatisticsDTO>` whose `.data` field
 * is validated against `ReservationStatisticsSchema` before being returned.
 * `buildPdfReportUrl` builds the URL for the existing PDF download endpoint.
 */
import { createApiClient, HttpError } from './base.js';
import { ReservationStatisticsSchema, AvailableMonthsSchema } from '../schemas/report.js';

// ─── HTTP client ──────────────────────────────────────────────────────────────

const api = createApiClient({
  baseURL: import.meta.env.VITE_API_URL ?? 'http://localhost:8080',
  headers: { Accept: 'application/json' },
});

// ─── Error helpers ────────────────────────────────────────────────────────────

function resolveErrorMessage(error) {
  const serverMessage = error.data?.message;
  const defaults = {
    0:   'No se pudo conectar con el servidor. Verifica tu conexión.',
    400: serverMessage || 'Los parámetros de consulta no son válidos.',
    401: 'No autorizado.',
    403: 'No tienes permisos para consultar los reportes.',
    500: 'Error interno del servidor. Intenta de nuevo más tarde.',
  };
  return defaults[error.status] || serverMessage || `Error inesperado (${error.status}).`;
}

// ─── Public API ───────────────────────────────────────────────────────────────

/**
 * Returns aggregated statistics for the Reportes y Estadísticas dashboard.
 *
 * Calls `GET /api/v1/reports/statistics?scope=…[&anchor=…]` and validates
 * the response payload against {@link ReservationStatisticsSchema}.
 *
 * @param {{ scope?: 'MENSUAL'|'SEMESTRAL', anchor?: string }} [params={}]
 *   `scope`  — period granularity (default `'MENSUAL'`).
 *   `anchor` — `yyyy-MM` for MENSUAL scope, or semester UUID for SEMESTRAL scope.
 *              Omit or pass an empty string to let the backend use the default period.
 * @returns {Promise<import('../schemas/report.js').ReservationStatisticsSchema._type>}
 * @throws {Error} with a user-friendly message on network or server errors.
 */
export async function getReservationStatistics({ scope = 'MENSUAL', anchor = '' } = {}) {
  try {
    const qs = new URLSearchParams({ scope });
    if (anchor) qs.set('anchor', anchor);

    const { data } = await api.get(`/api/v1/reports/statistics?${qs}`);
    return ReservationStatisticsSchema.parse(data.data);
  } catch (error) {
    if (error instanceof HttpError) throw new Error(resolveErrorMessage(error));
    throw error;
  }
}

/**
 * Returns the `yyyy-MM` months that have at least one active reservation, newest first.
 *
 * Calls `GET /api/v1/reports/available-months` and validates the response payload against
 * {@link AvailableMonthsSchema}. Used to populate the MENSUAL scope's period dropdown so it
 * only lists months that actually have data.
 *
 * @returns {Promise<string[]>} distinct `yyyy-MM` strings, newest first
 * @throws {Error} with a user-friendly message on network or server errors.
 */
export async function getAvailableMonths() {
  try {
    const { data } = await api.get('/api/v1/reports/available-months');
    return AvailableMonthsSchema.parse(data.data);
  } catch (error) {
    if (error instanceof HttpError) throw new Error(resolveErrorMessage(error));
    throw error;
  }
}

/**
 * Builds the URL for the existing PDF report endpoint.
 * Used by the "Exportar PDF" button in ReportsPage — opened in a new tab.
 *
 * @param {{ period?: 'MES_EN_CURSO'|'MES_ANTERIOR', classroomUuid?: string }} [params={}]
 * @returns {string} Full URL to the PDF download endpoint.
 */
export function buildPdfReportUrl({ period = 'MES_EN_CURSO', classroomUuid } = {}) {
  const base = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';
  const qs = new URLSearchParams({ period });
  if (classroomUuid) qs.set('classroomUuid', classroomUuid);
  return `${base}/api/v1/reports/reservations?${qs}`;
}
