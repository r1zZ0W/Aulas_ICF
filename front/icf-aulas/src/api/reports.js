/**
 * @fileoverview API client for the Reportes y Estadísticas module.
 *
 * `getReservationStatistics` calls the real backend endpoint:
 *   GET /api/v1/reports/statistics?scope=MONTHLY|SEMESTER[&anchor=...]
 *
 * The response is an `ApiResponse<ReservationStatisticsDTO>` whose `.data` field
 * is validated against `ReservationStatisticsSchema` before being returned.
 * `downloadReservationReportPdf` fetches the PDF report as an authenticated Blob.
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
 * @param {{ scope?: 'MONTHLY'|'SEMESTER', anchor?: string }} [params={}]
 *   `scope`  — period granularity (default `'MONTHLY'`).
 *   `anchor` — `yyyy-MM` for MONTHLY scope, or semester UUID for SEMESTER scope.
 *              Omit or pass an empty string to let the backend use the default period.
 * @returns {Promise<import('../schemas/report.js').ReservationStatisticsSchema._type>}
 * @throws {Error} with a user-friendly message on network or server errors.
 */
export async function getReservationStatistics({ scope = 'MONTHLY', anchor = '' } = {}) {
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
 * {@link AvailableMonthsSchema}. Used to populate the MONTHLY scope's period dropdown so it
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
 * Downloads the reservations report PDF as an authenticated Blob.
 *
 * Calls `GET /api/v1/reports/reservations?period=…[&classroomUuid=…]` through the shared
 * client so the Bearer token is injected (the previous `<a target="_blank">` approach sent
 * no Authorization header). The backend responds with `application/pdf`, which `base.js`
 * parses into a Blob; error responses stay JSON and surface through {@link HttpError}.
 *
 * The filename is computed client-side, mirroring the backend's `Content-Disposition`
 * logic (ReportController): reading the header would require exposing it via CORS.
 *
 * @param {{ period?: 'CURRENT_MONTH'|'PREVIOUS_MONTH', classroomUuid?: string }} [params={}]
 * @returns {Promise<{ blob: Blob, filename: string }>} PDF content plus suggested filename.
 * @throws {Error} with a user-friendly message on network or server errors.
 */
export async function downloadReservationReportPdf({ period = 'CURRENT_MONTH', classroomUuid } = {}) {
  try {
    const qs = new URLSearchParams({ period });
    if (classroomUuid) qs.set('classroomUuid', classroomUuid);

    const { data } = await api.get(`/api/v1/reports/reservations?${qs}`, {
      headers: { Accept: 'application/pdf' },
    });

    // Mirror of the backend's filename: yyyy-MM of the *selected* month, not always today's.
    const now = new Date();
    if (period === 'PREVIOUS_MONTH') now.setMonth(now.getMonth() - 1, 1);
    const yyyyMM = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;

    return { blob: data, filename: `reporte-reservas-${yyyyMM}.pdf` };
  } catch (error) {
    if (error instanceof HttpError) throw new Error(resolveErrorMessage(error));
    throw error;
  }
}
