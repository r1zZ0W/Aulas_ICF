import LoadingState from '../LoadingState/LoadingState';
import './DataTable.css';

/**
 * Generic, reusable data table component.
 *
 * Anti-flash behaviour: when `loading` is true but rows from a previous fetch
 * are still present (e.g. with keepPreviousData / placeholderData), the table
 * body is kept visible and subtly dimmed instead of being replaced by the full-
 * page spinner. The spinner only appears on the very first load (no prior rows).
 *
 * @param {Array<{key: string, header: string, render?: (row: any) => React.ReactNode, align?: 'left'|'right'|'center', width?: string}>} columns
 * @param {any[]}   rows           - Data rows to display (may be the previous page's rows while fetching).
 * @param {(row: any) => string|number} rowKey - Key extractor for each row.
 * @param {boolean} [loading=false]       - Whether a fetch is in flight.
 * @param {string}  [loadingMessage]      - Text shown in the full-page loader (first load only).
 * @param {React.ReactNode} [emptyState]  - Rendered when rows is empty and not loading.
 * @param {string}  [className='']
 */
export default function DataTable({
  columns = [],
  rows = [],
  rowKey,
  loading = false,
  loadingMessage = 'Cargando…',
  emptyState,
  className = '',
}) {
  const hasRows = rows.length > 0;

  return (
    <div className={`data-table-wrap ${className}`.trim()}>
      <table
        className={`data-table${loading && hasRows ? ' data-table--loading' : ''}`}
        aria-busy={loading}
      >
        <thead className="data-table__head">
          <tr>
            {columns.map((col) => (
              <th
                key={col.key}
                className="data-table__th"
                style={{ width: col.width, textAlign: col.align ?? 'left' }}
              >
                {col.header}
              </th>
            ))}
          </tr>
        </thead>

        {hasRows && (
          <tbody className="data-table__body">
            {rows.map((row) => (
              <tr key={rowKey ? rowKey(row) : JSON.stringify(row)} className="data-table__row">
                {columns.map((col) => (
                  <td
                    key={col.key}
                    className="data-table__td"
                    style={{ textAlign: col.align ?? 'left' }}
                  >
                    {col.render ? col.render(row) : row[col.key]}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        )}
      </table>

      {/* Full-page spinner only on the very first load (no prior rows). */}
      {loading && !hasRows && (
        <div className="data-table__state">
          <LoadingState message={loadingMessage} />
        </div>
      )}

      {!loading && !hasRows && emptyState && (
        <div className="data-table__state">{emptyState}</div>
      )}
    </div>
  );
}
