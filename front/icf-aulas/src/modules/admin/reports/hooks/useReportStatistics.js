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
 *   stats:      import('../../../../schemas/report.js').ReservationStatisticsSchema._type | undefined,
 *   loading:    boolean,
 *   isFetching: boolean,
 *   error:      Error | null,
 * }}
 */
export function useReportStatistics({ scope = 'MONTHLY', anchor = '' } = {}) {
  const { data, isLoading, isFetching, isError, error, refetch } = useQuery({
    queryKey: ['reports', 'statistics', { scope, anchor }],
    queryFn: () => getReservationStatistics({ scope, anchor }),
    placeholderData: keepPreviousData,
    staleTime: 60_000, // 1 min — stats don't need to be real-time
  });

  return {
    stats: data,
    loading: isLoading,
    // `keepPreviousData` means `isLoading` is false while a *new* period's data is
    // still in flight and the charts are showing the *previous* period's numbers —
    // `isFetching` is what callers must check to know "what's on screen right now
    // matches `scope`/`anchor`". The PDF export button relies on this to avoid
    // capturing a cover page for one period while the charts still show another.
    isFetching,
    isError,
    error: error ?? null,
    refetch,
  };
}
