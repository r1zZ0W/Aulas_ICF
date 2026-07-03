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
 * @param {number}  [opts.debounce=300]        - Debounce delay for the search input in ms.
 * @param {number}  [opts.minSearchLength=0]   - Minimum characters before writing to the URL.
 *   - 0 (default): every non-empty debounced value is written → retrocompatible.
 *   - N > 0: values shorter than N are NOT written; the URL (and thus the API query)
 *     stays frozen at its previous value until the user reaches N chars or clears to 0.
 *     This prevents the "ghost data" effect where 1-2 characters trigger a massive fetch.
 * @returns {{
 *   searchInput:    string,
 *   setSearchInput: (value: string) => void,
 *   search:         string,
 *   page:           number,
 *   setPage:        (page: number) => void,
 * }}
 */
export function usePagination({ debounce = 300, minSearchLength = 0 } = {}) {
  const [params, setParams] = useSearchParams();

  // Read initial values from the URL so a hard refresh restores context.
  const urlSearch = params.get('search') ?? '';
  const urlPage   = Math.max(0, Number(params.get('page')) || 0);

  // Local input value — updates immediately on each keystroke for snappy UI.
  const [searchInput, setSearchInputState] = useState(urlSearch);

  // Debounced input — decides when to write the search term to the URL.
  const debouncedInput = useDebouncedValue(searchInput, debounce);

  // Skip the initial effect run so we don't overwrite the URL on mount.
  const isMounted = useRef(false);

  useEffect(() => {
    if (!isMounted.current) {
      isMounted.current = true;
      return;
    }
    const len = debouncedInput.trim().length;
    if (len === 0 || len >= minSearchLength) {
      setParams(
        (prev) => {
          const next = new URLSearchParams(prev);
          if (debouncedInput) next.set('search', debouncedInput);
          else next.delete('search');
          next.delete('page'); // reset to page 0 whenever the applied search changes
          return next;
        },
        { replace: true } // don't pollute browser history on every keystroke
      );
    }
    // 0 < len < minSearchLength → do nothing; URL stays frozen at previous value
  }, [debouncedInput]); // eslint-disable-line react-hooks/exhaustive-deps

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

  // search = the URL-applied value (what the API is actually querying).
  // When minSearchLength > 0 and input is in the "limbo" range (1..N-1),
  // this returns the last applied value so React Query keeps its cache intact.
  return { searchInput, setSearchInput, search: urlSearch, page: urlPage, setPage };
}
