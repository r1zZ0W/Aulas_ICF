/**
 * @fileoverview Centralizes paginated + searchable list state in URL query params.
 *
 * Persisting state in the URL means:
 *  - Refreshing the page preserves the current search and page.
 *  - Links can be copied and shared with exact context.
 *  - Browser back/forward navigate through search history naturally.
 *
 * Usage:
 *   const { searchInput, setSearchInput, search, page, setPage } = usePagination()
 *
 *  - `searchInput` — bind to the <Buscador value> prop (immediate feedback).
 *  - `setSearchInput` — bind to the <Buscador onChange> handler.
 *  - `search` — debounced; pass to the API / React Query key.
 *  - `page` / `setPage` — pass to <Pagination> and the API.
 */
import { useState, useEffect, useRef } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useDebouncedValue } from './useDebouncedValue';

/**
 * @param {object}  [opts={}]
 * @param {number}  [opts.debounce=300] - Debounce delay for the search input in ms.
 * @returns {{
 *   searchInput:    string,
 *   setSearchInput: (value: string) => void,
 *   search:         string,
 *   page:           number,
 *   setPage:        (page: number) => void,
 * }}
 */
export function usePagination({ debounce = 300 } = {}) {
  const [params, setParams] = useSearchParams();

  // Read initial values from the URL so a hard refresh restores context.
  const urlSearch = params.get('search') ?? '';
  const urlPage   = Math.max(0, Number(params.get('page')) || 0);

  // Local input value — updates immediately on each keystroke for snappy UI.
  const [searchInput, setSearchInputState] = useState(urlSearch);

  // Debounced value — written to the URL and used as the API query key.
  const search = useDebouncedValue(searchInput, debounce);

  // Skip the initial effect run so we don't overwrite the URL on mount.
  const isMounted = useRef(false);

  useEffect(() => {
    if (!isMounted.current) {
      isMounted.current = true;
      return;
    }
    setParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        if (search) next.set('search', search);
        else next.delete('search');
        next.delete('page'); // reset to page 0 whenever the search term changes
        return next;
      },
      { replace: true } // don't pollute the browser history on every keystroke
    );
  }, [search]); // eslint-disable-line react-hooks/exhaustive-deps

  function setSearchInput(value) {
    setSearchInputState(value);
  }

  function setPage(newPage) {
    setParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        if (newPage > 0) next.set('page', String(newPage));
        else next.delete('page');
        return next;
      },
      { replace: true }
    );
  }

  return { searchInput, setSearchInput, search, page: urlPage, setPage };
}
