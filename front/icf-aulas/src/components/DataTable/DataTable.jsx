import LoadingState from '../LoadingState/LoadingState';
import './DataTable.css';

/**
 * Generic, reusable data table component.
 *
 * @param {Array<{key: string, header: string, render?: (row: any) => React.ReactNode, align?: 'left'|'right'|'center', width?: string}>} columns
 * @param {any[]} rows - Data rows to display
 * @param {(row: any) => string|number} rowKey - Key extractor for each row
 * @param {boolean} [loading=false] - Show loading state instead of table body
 * @param {React.ReactNode} [emptyState] - Node rendered when rows is empty and not loading
 * @param {string} [className='']
 */
export default function DataTable({
  columns = [],
  rows = [],
  rowKey,
  loading = false,
  emptyState,
  className = '',
}) {
  return (
    <div className={`data-table-wrap ${className}`.trim()}>
      <table className="data-table" aria-busy={loading}>
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

        {!loading && rows.length > 0 && (
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

      {loading && (
        <div className="data-table__state">
          <LoadingState message="Cargando usuarios…" />
        </div>
      )}

      {!loading && rows.length === 0 && emptyState && (
        <div className="data-table__state">{emptyState}</div>
      )}
    </div>
  );
}
