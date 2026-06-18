package mx.unam.icf.aulas.kernel.infrastructure.web.paging;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares, per controller parameter, the set of entity fields that the client
 * is allowed to sort by.
 *
 * <p>Placed on a {@link PageCriteria} controller parameter, this annotation is
 * read by {@link PageCriteriaArgumentResolver} to validate the {@code sort}
 * query parameter and apply safe defaults. Any value outside {@link #value()}
 * causes a {@link mx.unam.icf.aulas.kernel.domain.exceptions.DomainException}
 * (mapped to HTTP 400 by the global exception handler).</p>
 *
 * <p>Example usage in a controller:</p>
 * <pre>{@code
 * @GetMapping
 * public ResponseEntity<ApiResponse<PagedResultDTO<UserResponseDTO>>> findAll(
 *         @SortWhitelist(
 *             value = {"createdAt", "email", "username"},
 *             defaultSort = "createdAt",
 *             defaultDirection = "desc")
 *         PageCriteria criteria) {
 *     return ok(userService.findAll(criteria.toPageable()));
 * }
 * }</pre>
 *
 * @author Ithera
 * @version 1.0
 * @see PageCriteria
 * @see PageCriteriaArgumentResolver
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface SortWhitelist {

    /**
     * The entity/DTO property names that are allowed as {@code ?sort} values.
     * Must match actual Spring Data property names (camelCase).
     */
    String[] value();

    /**
     * Default sort field applied when the client omits {@code ?sort}.
     * Must be one of the values listed in {@link #value()}.
     */
    String defaultSort() default "createdAt";

    /**
     * Default sort direction when the client omits {@code ?direction}.
     * Accepted values (case-insensitive): {@code "asc"} or {@code "desc"}.
     */
    String defaultDirection() default "desc";
}
