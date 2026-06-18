package mx.unam.icf.aulas.kernel.infrastructure.web.paging;

import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Immutable value object that encapsulates validated pagination and sort
 * parameters resolved from an HTTP request.
 *
 * <p>Instances are produced exclusively by {@link PageCriteriaArgumentResolver}
 * after applying defaults, capping {@code size} to {@link PageCriteriaArgumentResolver#MAX_SIZE},
 * and validating the {@code sort} field against the caller's {@link SortWhitelist}.
 * Controllers receive an already-safe object and simply call {@link #toPageable()}.</p>
 *
 * @param page      zero-based page index (non-negative)
 * @param size      number of items per page (between 1 and {@link PageCriteriaArgumentResolver#MAX_SIZE})
 * @param sort      validated sort property name
 * @param direction sort direction
 *
 * @author Ithera
 * @version 1.0
 * @see SortWhitelist
 * @see PageCriteriaArgumentResolver
 */
public record PageCriteria(int page, int size, String sort, Sort.Direction direction) {

    /**
     * Converts these criteria into a Spring Data {@link Pageable} suitable for
     * passing directly to any repository method that accepts {@code Pageable}.
     *
     * @return a {@link PageRequest} with the page, size, and sort configured
     */
    public @NonNull Pageable toPageable() {
        return PageRequest.of(page, size, Sort.by(direction, sort));
    }
}
