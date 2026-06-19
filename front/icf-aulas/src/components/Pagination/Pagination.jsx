import './Pagination.css';

/**
 * Generic paginator: info text + Anterior / Siguiente buttons.
 *
 * @param {object}   props
 * @param {number}   props.page          - Current zero-based page index.
 * @param {number}   props.totalPages    - Total page count from the server.
 * @param {function} props.onPageChange  - Called with the next page index.
 * @param {number}   props.showing       - Number of rows visible on this page.
 * @param {number}   props.total         - Total element count from the server.
 * @param {string}   [props.noun='elemento'] - Singular noun for the info text.
 * @param {boolean}  [props.searchActive=false] - Appends "(búsqueda activa)" when true.
 */
export default function Pagination({
  page,
  totalPages,
  onPageChange,
  showing,
  total,
  noun = 'elemento',
  searchActive = false,
}) {
  const safeTotalPages = Math.max(1, totalPages);
  const isLastPage = page >= safeTotalPages - 1;

  return (
    <div className="pagination">
      <p className="pagination__info">
        Mostrando {showing} de {total} {noun}{total !== 1 ? 's' : ''}
        {searchActive && ' (búsqueda activa)'}
      </p>
      <div className="pagination__controls">
        <button
          type="button"
          className="pagination__btn"
          onClick={() => onPageChange(Math.max(0, page - 1))}
          disabled={page === 0}
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
