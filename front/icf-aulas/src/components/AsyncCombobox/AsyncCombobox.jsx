import { useState, useRef, useEffect, useLayoutEffect } from 'react';
import { createPortal } from 'react-dom';
import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { Search, X, Loader2 } from 'lucide-react';
import { useDebouncedValue } from '../../hooks/useDebouncedValue';
import './AsyncCombobox.css';

/**
 * Walks up from `el` looking for the nearest ancestor with its own scroll (an
 * `overflow-y: auto|scroll` container), stopping at `document.body`.
 *
 * Needed because this component can be mounted inside a modal whose body scrolls
 * independently of `window` (e.g. `.reserva-modal__inner`) — see the dropdown
 * positioning effect below for why both `window` and this ancestor are tracked.
 *
 * @param {HTMLElement|null} el
 * @returns {HTMLElement|null}
 */
function getScrollParent(el) {
  let node = el?.parentElement ?? null;
  while (node && node !== document.body) {
    const { overflowY } = window.getComputedStyle(node);
    if (overflowY === 'auto' || overflowY === 'scroll') return node;
    node = node.parentElement;
  }
  return null;
}

/**
 * Generic, entity-agnostic async search combobox.
 *
 * Mechanically it knows nothing about "users" or any other domain: callers supply
 * `queryFn` (fetch results for a search term), `getLabel`/`getValue` (required), and
 * optionally `getSecondaryLabel`/`getBadge` for a richer row/chip display. A thin
 * domain-specific wrapper (e.g. `UserCombobox`) supplies those callbacks.
 *
 * Two visual modes:
 *  - No selection: a search input + floating dropdown of results, debounced.
 *  - Selection made (`value` truthy): the input is replaced by a compact "chip"
 *    (label + secondary label + badge + a clear "×"), mirroring the file-chip pattern
 *    already used in `ReservaModal` for the roster upload.
 *
 * @param {{
 *   value: any,
 *   onChange: (item: any|null) => void,
 *   queryFn: (search: string) => Promise<any[]>,
 *   getLabel: (item: any) => string,
 *   getValue: (item: any) => string|number,
 *   getSecondaryLabel?: (item: any) => string,
 *   getBadge?: (item: any) => string|null,
 *   placeholder?: string,
 *   debounceMs?: number,
 *   disabled?: boolean,
 *   className?: string,
 * }} props
 */
export default function AsyncCombobox({
  value,
  onChange,
  queryFn,
  getLabel,
  getValue,
  getSecondaryLabel,
  getBadge,
  placeholder = 'Buscar…',
  debounceMs = 300,
  disabled = false,
  className = '',
}) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [dropdownStyle, setDropdownStyle] = useState(null);

  const containerRef = useRef(null);
  const triggerRef = useRef(null);
  const inputRef = useRef(null);
  const clearButtonRef = useRef(null);

  const debouncedQuery = useDebouncedValue(query, debounceMs);

  // `placeholderData: keepPreviousData` prevents the dropdown from flashing
  // empty/"Buscando…" on every keystroke — the previous results stay visible
  // (dimmed via `isFetching` below) while the new term resolves. `staleTime: 0`
  // because an incremental search term is never "fresh" relative to the next one;
  // a short `gcTime` keeps per-keystroke cache entries from accumulating in memory
  // over a long search session.
  const {
    data: items = [],
    isLoading,
    isFetching,
    isError,
    error,
  } = useQuery({
    queryKey: ['async-combobox', debouncedQuery],
    queryFn: () => queryFn(debouncedQuery),
    enabled: open && !value,
    placeholderData: keepPreviousData,
    staleTime: 0,
    gcTime: 10_000,
  });

  // ── Dropdown positioning (portal + fixed) ──────────────────────────────────
  // Mirrors src/components/Select/Select.jsx's approach. `window`'s scroll listener
  // uses capture:true, which (scroll not bubbling notwithstanding) still fires for
  // scroll on any descendant during the capture phase — but this component can also
  // live inside a modal with its own overflow-y:auto (e.g. .reserva-modal__inner),
  // so the nearest scrollable ancestor gets its own listener too, belt-and-suspenders.
  useLayoutEffect(() => {
    if (!open) {
      setDropdownStyle(null);
      return;
    }
    const updatePosition = () => {
      if (!triggerRef.current) return;
      const rect = triggerRef.current.getBoundingClientRect();
      const spaceBelow = window.innerHeight - rect.bottom;
      const dropdownHeight = Math.min(280, Math.max(items.length * 56 + 12, 80));
      const openDown = spaceBelow >= dropdownHeight || spaceBelow >= rect.top;

      setDropdownStyle({
        position: 'fixed',
        left: rect.left,
        width: rect.width,
        top: openDown ? rect.bottom + 6 : undefined,
        bottom: openDown ? undefined : window.innerHeight - rect.top + 6,
        zIndex: 10050,
      });
    };
    updatePosition();

    const scrollParent = getScrollParent(triggerRef.current);
    window.addEventListener('scroll', updatePosition, true);
    window.addEventListener('resize', updatePosition);
    scrollParent?.addEventListener('scroll', updatePosition);
    return () => {
      window.removeEventListener('scroll', updatePosition, true);
      window.removeEventListener('resize', updatePosition);
      scrollParent?.removeEventListener('scroll', updatePosition);
    };
  }, [open, items.length]);

  // ── Click-outside / Escape to close ────────────────────────────────────────
  useEffect(() => {
    if (!open) return;
    const handleClickOutside = (e) => {
      if (containerRef.current?.contains(e.target)) return;
      const dropdowns = document.querySelectorAll('.async-combobox__dropdown--portal');
      for (const dd of dropdowns) {
        if (dd.contains(e.target)) return;
      }
      setOpen(false);
    };
    const handleEscape = (e) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', handleClickOutside);
    document.addEventListener('keydown', handleEscape);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('keydown', handleEscape);
    };
  }, [open]);

  // ── Selection / clearing ───────────────────────────────────────────────────

  const handleSelect = (item) => {
    onChange?.(item);
    setOpen(false);
    setQuery('');
    // Focus follows the selection: the search input is about to be replaced by the
    // chip, so hand the keyboard focus to the chip's clear button instead of letting
    // it fall off the DOM (a11y — see AsyncCombobox plan notes on focus management).
    requestAnimationFrame(() => clearButtonRef.current?.focus());
  };

  const handleClear = () => {
    onChange?.(null);
    // Mirror of the above: return focus to the search input that's about to reappear.
    requestAnimationFrame(() => inputRef.current?.focus());
  };

  const label = value ? getLabel(value) : '';
  const secondaryLabel = value && getSecondaryLabel ? getSecondaryLabel(value) : '';
  const badge = value && getBadge ? getBadge(value) : null;

  // Only the very first fetch (no cached/placeholder data yet) shows the full
  // "Buscando…" message; a refetch with existing results just dims them (see
  // DataTable.jsx's identical anti-flash convention for loading vs. refetching).
  const showDropdown = open && !value && dropdownStyle;
  const dropdownContent = showDropdown ? (
    <div
      className="async-combobox__dropdown async-combobox__dropdown--portal"
      style={dropdownStyle}
      role="listbox"
    >
      {isLoading ? (
        <div className="async-combobox__state">
          <Loader2 size={16} className="async-combobox__spinner" />
          Buscando…
        </div>
      ) : isError ? (
        <div className="async-combobox__state async-combobox__state--error">
          {error?.message || 'No se pudo buscar. Intenta de nuevo.'}
        </div>
      ) : items.length === 0 ? (
        <div className="async-combobox__state">Sin resultados</div>
      ) : (
        <ul
          className={`async-combobox__list${isFetching ? ' async-combobox__list--dim' : ''}`}
        >
          {items.map((item) => {
            const itemBadge = getBadge ? getBadge(item) : null;
            return (
              <li key={getValue(item)}>
                <button
                  type="button"
                  role="option"
                  className="async-combobox__option"
                  onClick={() => handleSelect(item)}
                >
                  <span className="async-combobox__option-label">{getLabel(item)}</span>
                  {getSecondaryLabel && (
                    <span className="async-combobox__option-secondary">
                      {getSecondaryLabel(item)}
                    </span>
                  )}
                  {itemBadge && (
                    <span className="async-combobox__badge">{itemBadge}</span>
                  )}
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  ) : null;

  return (
    <div
      ref={containerRef}
      className={`async-combobox ${disabled ? 'async-combobox--disabled' : ''} ${className}`.trim()}
    >
      {value ? (
        <div className="async-combobox__chip" ref={triggerRef}>
          <div className="async-combobox__chip-text">
            <span className="async-combobox__chip-label">{label}</span>
            {secondaryLabel && (
              <span className="async-combobox__chip-secondary">{secondaryLabel}</span>
            )}
          </div>
          {badge && <span className="async-combobox__badge">{badge}</span>}
          <button
            ref={clearButtonRef}
            type="button"
            className="async-combobox__clear"
            onClick={handleClear}
            disabled={disabled}
            aria-label="Quitar selección"
          >
            <X size={15} />
          </button>
        </div>
      ) : (
        <div className="async-combobox__input-wrap" ref={triggerRef}>
          <Search size={16} className="async-combobox__search-icon" />
          <input
            ref={inputRef}
            type="text"
            className="async-combobox__input"
            value={query}
            onChange={(e) => { setQuery(e.target.value); setOpen(true); }}
            onFocus={() => setOpen(true)}
            placeholder={placeholder}
            disabled={disabled}
            role="combobox"
            aria-expanded={open}
            aria-haspopup="listbox"
          />
        </div>
      )}
      {showDropdown && createPortal(dropdownContent, document.body)}
    </div>
  );
}
