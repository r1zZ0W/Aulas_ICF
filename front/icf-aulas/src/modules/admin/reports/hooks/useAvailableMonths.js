/**
 * @fileoverview Custom hook that fetches the months with reservation data for the
 * Reportes y Estadísticas dashboard's MONTHLY period dropdown.
 *
 * Query key structure:
 *   ['reports', 'available-months']
 *
 * Same `staleTime` rationale as `useReportStatistics`: the set of months with data changes
 * at most once a day (when a new month starts), so a 1-minute cache is more than enough
 * freshness without refetching on every render.
 */
import { useQuery } from '@tanstack/react-query';
import { getAvailableMonths } from '../../../../api/reports.js';

/**
 * @returns {{
 *   months:  string[],   yyyy-MM strings, newest first (empty array while loading/on error)
 *   loading: boolean,
 *   error:   Error | null,
 * }}
 */
export function useAvailableMonths() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['reports', 'available-months'],
    queryFn: getAvailableMonths,
    staleTime: 60_000,
  });

  return {
    months: data ?? [],
    loading: isLoading,
    error: error ?? null,
  };
}
