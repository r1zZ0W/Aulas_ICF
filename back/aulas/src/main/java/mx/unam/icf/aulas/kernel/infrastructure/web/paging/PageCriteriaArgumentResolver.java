package mx.unam.icf.aulas.kernel.infrastructure.web.paging;

import lombok.extern.slf4j.Slf4j;
import mx.unam.icf.aulas.kernel.domain.exceptions.DomainException;
import org.jspecify.annotations.NonNull;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Global {@link HandlerMethodArgumentResolver} that builds and validates a
 * {@link PageCriteria} from HTTP query parameters.
 *
 * <p>This resolver activates whenever a controller parameter is of type
 * {@link PageCriteria} and carries the {@link SortWhitelist} annotation.
 * It centralizes pagination logic — defaults, capping, direction, and sort
 * whitelist enforcement — in one place, keeping controllers free of repetitive
 * {@code @RequestParam} declarations.</p>
 *
 * <h3>Query parameters</h3>
 * <table border="1">
 *   <tr><th>Param</th><th>Default</th><th>Notes</th></tr>
 *   <tr><td>{@code page}</td><td>0</td><td>Zero-based; negatives → 0.</td></tr>
 *   <tr><td>{@code size}</td>
 *       <td>{@link #DEFAULT_UNPAGED_SIZE} when absent, {@link #DEFAULT_SIZE} if only {@code page} sent</td>
 *       <td>Capped to {@link #MAX_SIZE}.</td></tr>
 *   <tr><td>{@code sort}</td><td>From {@link SortWhitelist#defaultSort()}</td>
 *       <td>Must be in {@link SortWhitelist#value()}; otherwise throws {@link DomainException} → HTTP 400.</td></tr>
 *   <tr><td>{@code direction}</td><td>From {@link SortWhitelist#defaultDirection()}</td>
 *       <td>Accepted values: {@code asc}, {@code desc} (case-insensitive).</td></tr>
 * </table>
 *
 * <h3>Unpaged behavior</h3>
 * <p>When <em>neither</em> {@code page} nor {@code size} is present in the request,
 * the resolver returns a single large page of size {@link #DEFAULT_UNPAGED_SIZE}.
 * This preserves backward compatibility for frontend consumers that have not yet
 * adopted pagination (they receive the same envelope format
 * {@code { items, totalElements, ... }} but with all records in one page).</p>
 *
 * <h3>Registration</h3>
 * <p>Registered via {@link mx.unam.icf.aulas.kernel.infrastructure.web.WebConfig}.</p>
 *
 * @author Ithera
 * @version 1.0
 * @see PageCriteria
 * @see SortWhitelist
 */
@Slf4j
public class PageCriteriaArgumentResolver implements HandlerMethodArgumentResolver {

    /** Default page size when pagination params are present but {@code size} is omitted. */
    public static final int DEFAULT_SIZE = 20;

    /**
     * Maximum allowed page size. Requests with {@code size} above this value
     * are silently capped rather than rejected.
     */
    public static final int MAX_SIZE = 100;

    /**
     * Page size used when the client sends no pagination params at all, producing
     * a single large page that maintains backward compatibility with pre-pagination
     * frontend consumers.
     */
    public static final int DEFAULT_UNPAGED_SIZE = 1000;

    // ── HandlerMethodArgumentResolver ────────────────────────────────────────

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return PageCriteria.class.equals(parameter.getParameterType())
                && parameter.hasParameterAnnotation(SortWhitelist.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  @NonNull NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {

        SortWhitelist annotation = parameter.getParameterAnnotation(SortWhitelist.class);
        if (annotation == null)
            throw new IllegalStateException("@SortWhitelist annotation missing on PageCriteria parameter");

        Set<String> allowed = Arrays.stream(annotation.value()).collect(Collectors.toUnmodifiableSet());
        String defaultSortField = annotation.defaultSort();
        String defaultDirValue  = annotation.defaultDirection();

        // ── Raw params ────────────────────────────────────────────────────────
        String pageParam      = webRequest.getParameter("page");
        String sizeParam      = webRequest.getParameter("size");
        String sortParam      = webRequest.getParameter("sort");
        String directionParam = webRequest.getParameter("direction");

        boolean hasPaginationParams = pageParam != null || sizeParam != null;

        // ── Page index ────────────────────────────────────────────────────────
        int page = 0;
        if (pageParam != null) {
            try { page = Math.max(0, Integer.parseInt(pageParam)); }
            catch (NumberFormatException e) {
                log.warn("Invalid 'page' parameter '{}', defaulting to 0", pageParam);
            }
        }

        // ── Page size ─────────────────────────────────────────────────────────
        int size;
        if (!hasPaginationParams) {
            size = DEFAULT_UNPAGED_SIZE;
        } else if (sizeParam != null) {
            try { size = Math.clamp(Integer.parseInt(sizeParam), 1, MAX_SIZE); }
            catch (NumberFormatException e) { size = DEFAULT_SIZE; }
        } else {
            size = DEFAULT_SIZE;
        }

        // ── Sort field (whitelist-validated) ──────────────────────────────────
        String sort;
        if (sortParam != null && !sortParam.isBlank()) {
            if (!allowed.contains(sortParam))
                throw new DomainException(
                        "Invalid sort field '" + sortParam + "'. Allowed fields: " + allowed);
            sort = sortParam;
        } else {
            sort = defaultSortField;
        }

        // ── Direction ─────────────────────────────────────────────────────────
        Sort.Direction direction;
        if (directionParam != null && directionParam.equalsIgnoreCase("asc")) {
            direction = Sort.Direction.ASC;
        } else if (directionParam != null && directionParam.equalsIgnoreCase("desc")) {
            direction = Sort.Direction.DESC;
        } else {
            direction = "asc".equalsIgnoreCase(defaultDirValue)
                    ? Sort.Direction.ASC
                    : Sort.Direction.DESC;
        }

        log.debug("PageCriteria resolved: page={}, size={}, sort={}, direction={}", page, size, sort, direction);
        return new PageCriteria(page, size, sort, direction);
    }
}
