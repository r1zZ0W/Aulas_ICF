/**
 * @fileoverview Generic URL-synced filter state for an arbitrary set of query params.
 * All values live in the URL so browser history tracks filter changes and copying
 * the URL reproduces the exact filter state.
 */
import { useSearchParams } from 'react-router-dom';

/**
 * @param {string[]} keys - Param names to manage (e.g. ['status', 'classroomId', 'from', 'to']).
 * @returns {{
 *   values:       Record<string, string>,
 *   setFilter:    (key: string, value: string) => void,
 *   resetFilters: (extraKeys?: string[]) => void,
 * }}
 */
export function useUrlFilters(keys) {
  const [params, setParams] = useSearchParams();

  const values = Object.fromEntries(keys.map(k => [k, params.get(k) ?? '']));

  function setFilter(key, value) {
    setParams(prev => {
      const next = new URLSearchParams(prev);
      if (value) next.set(key, value);
      else next.delete(key);
      next.delete('page');
      return next;
    }, { replace: true });
  }

  /**
   * Atomic reset: deletes all managed keys + page + any extra keys in ONE setParams call.
   * Avoids double history entries that would result from two sequential setParams calls.
   *
   * @param {string[]} [extraKeys=[]] - Additional params to delete (e.g. ['search','sort','direction']).
   */
  function resetFilters(extraKeys = []) {
    setParams(prev => {
      const next = new URLSearchParams(prev);
      for (const k of [...keys, 'page', ...extraKeys]) {
        next.delete(k);
      }
      return next;
    }, { replace: true });
  }

  return { values, setFilter, resetFilters };
}
