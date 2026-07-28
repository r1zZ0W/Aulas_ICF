/**
 * @fileoverview API client for the Reportes y Estadísticas module.
 *
 * `getReservationStatistics` calls the real backend endpoint:
 *   GET /api/v1/reports/statistics?scope=MONTHLY|SEMESTER[&anchor=...]
 *
 * The response is an `ApiResponse<ReservationStatisticsDTO>` whose `.data` field
 * is validated against `ReservationStatisticsSchema` before being returned.
 *
 * PDF export of the dashboard is generated client-side (see
 * `modules/admin/reports/exportStatisticsPdf.js`) from the same statistics data
 * fetched here — there is no server-rendered PDF report anymore.
 */
import { createApiClient } from './base.js';
import { ReservationStatisticsSchema, AvailableMonthsSchema } from '../schemas/report.js';

const api = createApiClient();

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
 * @throws {import('../errors/ApiError.js').ApiError}
 */
export async function getReservationStatistics({ scope = 'MONTHLY', anchor = '' } = {}) {
  const qs = new URLSearchParams({ scope });
  if (anchor) qs.set('anchor', anchor);

  return api.getValidated(`/api/v1/reports/statistics?${qs}`, {
    schema: ReservationStatisticsSchema,
    overrides: { ACCESS_DENIED: 'No tienes permisos para consultar los reportes.' },
  });
}

/**
 * Returns the `yyyy-MM` months that have at least one active reservation, newest first.
 *
 * Calls `GET /api/v1/reports/available-months` and validates the response payload against
 * {@link AvailableMonthsSchema}. Used to populate the MONTHLY scope's period dropdown so it
 * only lists months that actually have data.
 *
 * @returns {Promise<string[]>} distinct `yyyy-MM` strings, newest first
 * @throws {import('../errors/ApiError.js').ApiError}
 */
export async function getAvailableMonths() {
  return api.getValidated('/api/v1/reports/available-months', { schema: AvailableMonthsSchema });
}
