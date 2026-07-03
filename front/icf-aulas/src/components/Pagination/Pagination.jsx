import './Pagination.css';

/**
 * Generic paginator: info text + Anterior / Siguiente buttons.
 *
 * @param {object}   props
 * @param {number}   props.page          - Current zero-based page index.
 * @param {number}   props.totalPages    - Total page count from the server.
 * @param {function} props.onPageChange  - Called with the next page index.
 * @param {number}   props.pageSize      - Number of rows visible on this page.
 * @param {number}   props.total         - Total element count from the server.
 * @param {string}   [props.noun='elemento'] - Singular noun for the info text.
 * @param {boolean}  [props.searchActive=false] - Appends "(búsqueda activa)" when true.
 */
export default function Pagination({
  page,
  totalPages,
  onPageChange,
  pageSize,
  total,
  noun = 'elemento',
  searchActive = false,
}) {
  const safeTotalPages = Math.max(1, totalPages);
  const currentPage = page >= safeTotalPages ? safeTotalPages - 1 : page;
  const isFirstPage = currentPage <= 0;
  const isLastPage = currentPage >= safeTotalPages - 1;
  const from = total === 0 ? 0 : currentPage * pageSize + 1;
  const to = Math.min(total, (currentPage + 1) * pageSize);

  return (
    <div className="pagination">
      <p className="pagination__info">
        Mostrando {from} al {to} de {total} {noun}{total !== 1 ? 's' : ''}
        {searchActive && ' (búsqueda activa)'}
      </p>
      <div className="pagination__controls">
        <button
          type="button"
          className="pagination__btn"
          onClick={() => onPageChange(Math.max(0, page - 1))}
          disabled={isFirstPage}
        >
          Anterior
        </button>
        <button
          type="button"
          className="pagination__btn"
          onClick={() => onPageChange(page + 1)}
          disabled={isLastPage}
        >
          Siguiente
        </button>
      </div>
    </div>
  );
}
