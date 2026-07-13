/**
 * @fileoverview Custom hook that encapsulates server-state logic for the
 * Reportes y Estadísticas dashboard.
 *
 * Query key structure:
 *   ['reports', 'statistics', { scope, anchor }]
 *
 * Using `keepPreviousData` so the charts don't flash to empty while the new
 * period data is loading — same pattern as `useReservationHistory`.
 */
import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { getReservationStatistics } from '../../../../api/reports.js';

/**
 * @param {object}                    params
 * @param {'MONTHLY'|'SEMESTER'}      [params.scope='MONTHLY']  Period granularity.
 * @param {string}                    [params.anchor='']         yyyy-MM (MONTHLY) or semester UUID (SEMESTER).
 *
 * @returns {{
 *   stats:   import('../../../../schemas/report.js').ReservationStatisticsSchema._type | undefined,
 *   loading: boolean,
 *   error:   Error | null,
 * }}
 */
export function useReportStatistics({ scope = 'MONTHLY', anchor = '' } = {}) {
  const { data, isLoading, error } = useQuery({
    queryKey: ['reports', 'statistics', { scope, anchor }],
    queryFn: () => getReservationStatistics({ scope, anchor }),
    placeholderData: keepPreviousData,
    staleTime: 60_000, // 1 min — stats don't need to be real-time
  });

  return {
    stats: data,
    loading: isLoading,
    error: error ?? null,
  };
}
