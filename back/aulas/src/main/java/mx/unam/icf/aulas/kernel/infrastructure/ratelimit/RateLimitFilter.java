package mx.unam.icf.aulas.kernel.infrastructure.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.unam.icf.aulas.kernel.infrastructure.web.FilterResponseWriter;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;

/**
 * Servlet filter that enforces IP-based rate limiting on sensitive authentication endpoints.
 *
 * <p>Uses a token-bucket algorithm (Bucket) with one bucket per client IP, stored in a
 * Caffeine cache bounded to 50 000 entries to prevent Memory DoS. Entries expire after
 * inactivity so the map shrinks automatically between bursts.</p>
 *
 * <p>X-Forwarded-For is only trusted when {@code app.rate-limit.trust-proxy=true}, which
 * must only be set when the service runs behind a known, trusted reverse proxy.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private final FilterResponseWriter filterResponseWriter;

    /** Maximum number of requests allowed within the refill window. */
    @Value("${app.rate-limit.capacity:5}")
    private int capacity;

    /** Length of the refill window in minutes. */
    @Value("${app.rate-limit.window-minutes:1}")
    private int windowMinutes;

    /**
     * When true, the rightmost value of X-Forwarded-For is used as the client IP.
     * Set to true ONLY behind a trusted reverse proxy (nginx, AWS ALB, etc.).
     * Defaults too false to prevent IP-spoofing bypass of rate limiting.
     */
    @Value("${app.rate-limit.trust-proxy:false}")
    private boolean trustProxy;

    /** Endpoints protected by this filter. */
    private static final Set<String> PROTECTED_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/refresh",
            "/api/v1/auth/reset-password",
            "/api/v1/users/register"
    );

    /**
     * Bounded Caffeine cache: one Bucket per client IP.
     * maximumSize prevents Memory DoS from IP-space exhaustion.
     * expireAfterAccess evicts idle entries automatically.
     */
    private Cache<String, Bucket> ipBuckets;

    @PostConstruct
    private void initCache() {
        ipBuckets = Caffeine.newBuilder()
                .maximumSize(50_000)
                .expireAfterAccess(Duration.ofMinutes(windowMinutes * 2L))
                .build();
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {

        if (!PROTECTED_PATHS.contains(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        Bucket bucket   = ipBuckets.get(clientIp, ip -> buildBucket());

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded — ip={} path={}", clientIp, request.getRequestURI());
            writeTooManyRequests(response);
        }
    }

    private Bucket buildBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillGreedy(capacity, Duration.ofMinutes(windowMinutes))
                        .build())
                .build();
    }

    /**
     * Resolves the real client IP.
     * X-Forwarded-For is only trusted when {@code trust-proxy=true} and a valid header is present.
     * When trusted, the rightmost value is used — it is set by the (trusted) proxy and cannot
     * be spoofed by the client, unlike the leftmost value.
     * @param request the incoming HTTP request
     * @return the resolved client IP address as a string
     */
    private String resolveClientIp(HttpServletRequest request) {
        if (trustProxy) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String[] parts = forwarded.split(",");
                return parts[parts.length - 1].strip();
            }
        }
        return request.getRemoteAddr();
    }

    /**
     *
     * @param response the HTTP response
     * @throws IOException
     */
    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        response.setHeader("Retry-After", String.valueOf(windowMinutes * 60));
        filterResponseWriter.writeError(
                response,
                HttpStatus.TOO_MANY_REQUESTS,
                "Too many requests. Please try again in " + windowMinutes + " minute(s)."
        );
    }
}
