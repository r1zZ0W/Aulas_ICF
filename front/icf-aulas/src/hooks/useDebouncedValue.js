/**
 * @fileoverview Returns a debounced copy of `value` that only updates after
 * `delay` ms of inactivity. Use this to delay server-side search calls until
 * the user pauses typing.
 */
import { useState, useEffect } from 'react';

/**
 * @template T
 * @param {T} value  - The value to debounce (typically a string from a search input).
 * @param {number} [delay=300] - Debounce delay in milliseconds.
 * @returns {T} The debounced value.
 */
export function useDebouncedValue(value, delay = 300) {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(id);
  }, [value, delay]);

  return debounced;
}
