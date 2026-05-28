package mx.unam.icf.aulas.kernel.app.dtos;

import mx.unam.icf.aulas.kernel.infrastructure.web.responses.ApiResponse;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Generic record representing a paged result set.
 *
 * <p>Framework-agnostic; belongs to the application/kernel layer.
 * Use this as the payload inside {@link ApiResponse} when returning paged data.
 * Instances must be created through the static factory methods {@link #empty()} or {@link #of}.</p>
 *
 * @param <T>           the type of items in the page (usually DTOs)
 * @param items         items for the current page
 * @param totalElements total number of elements across all pages
 * @param totalPages    total number of pages available
 * @param page          current page index (zero-based)
 * @param size          size of the page requested
 * @param first         {@code true} if this is the first page
 * @param last          {@code true} if this is the last page
 *
 * @see ApiResponse
 * @author Ithera
 * @version 2.0
 */
public record PagedResultDTO<T>(
        List<T> items,
        long totalElements,
        int totalPages,
        int page,
        int size,
        boolean first,
        boolean last
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Returns an empty page representation with all counters set to zero.
     *
     * @param <T> the item type
     * @return a {@link PagedResultDTO} with no items and no pages
     */
    public static <T> PagedResultDTO<T> empty() {
        return new PagedResultDTO<>(
                Collections.emptyList(),
                0L, 0, 0, 0,
                true, true
        );
    }

    /**
     * Builds a paged result without depending on any framework types.
     *
     * @param items         items of the current page (null-safe: treated as empty list)
     * @param totalElements total number of elements across all pages
     * @param totalPages    total pages available
     * @param page          current page index (zero-based)
     * @param size          page size requested
     * @param <T>           item type
     * @return a fully populated {@link PagedResultDTO}
     */
    public static <T> PagedResultDTO<T> of(List<T> items, long totalElements, int totalPages, int page, int size) {
        return new PagedResultDTO<>(
                items == null ? Collections.emptyList() : items,
                totalElements,
                totalPages,
                page,
                size,
                page == 0,
                totalPages <= 0 || page >= totalPages - 1
        );
    }
}
