/**
 * @fileoverview URL-synced sort state with whitelist sanitisation.
 * Reading sort/direction from the URL and sanitising against an allowed list prevents
 * 400 errors when a user manually edits the URL with an invalid sort field.
 */
import { useSearchParams } from 'react-router-dom';

/**
 * @param {object}   opts
 * @param {string}   opts.defaultSort       - Fallback sort field when URL value is invalid.
 * @param {'asc'|'desc'} [opts.defaultDirection='desc'] - Default sort direction.
 * @param {string[]} opts.allowed           - Whitelist of valid sort field names.
 * @returns {{
 *   sort:       string,
 *   direction:  'asc'|'desc',
 *   toggleSort: (field: string) => void,
 * }}
 */
export function useTableSort({ defaultSort, defaultDirection = 'desc', allowed }) {
  const [params, setParams] = useSearchParams();

  const rawSort = params.get('sort') ?? '';
  const rawDir  = params.get('direction') ?? '';

  const sort      = allowed.includes(rawSort) ? rawSort : defaultSort;
  const direction = (rawDir === 'asc' || rawDir === 'desc') ? rawDir : defaultDirection;

  function toggleSort(field) {
    setParams(prev => {
      const next = new URLSearchParams(prev);
      if (field === sort) {
        next.set('direction', direction === 'asc' ? 'desc' : 'asc');
      } else {
        next.set('sort', field);
        next.set('direction', defaultDirection);
      }
      next.delete('page');
      return next;
    }, { replace: true });
  }

  return { sort, direction, toggleSort };
}
