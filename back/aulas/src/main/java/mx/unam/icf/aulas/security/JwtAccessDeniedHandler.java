package mx.unam.icf.aulas.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.infrastructure.web.FilterResponseWriter;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Returns HTTP 403 when an authenticated user is blocked by Spring Security's filter chain.
 *
 * <p>There are two distinct 403 paths in this application:</p>
 * <ul>
 *   <li><b>Filter chain level</b> (this handler): triggered when URL-based rules in
 *       {@code HttpSecurity.authorizeHttpRequests} deny the request.</li>
 *   <li><b>MVC / AOP level</b> ({@code GlobalExceptionHandler.handleAccessDenied}):
 *       triggered when {@code @PreAuthorize} rejects the call inside the controller.</li>
 * </ul>
 * <p>Both paths return the same JSON envelope via {@link FilterResponseWriter} so clients
 * receive a uniform 403 structure regardless of which layer blocked the request.</p>
 */
@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final FilterResponseWriter filterResponseWriter;

    @Override
    public void handle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                       @NonNull AccessDeniedException accessDeniedException) throws IOException {
        filterResponseWriter.writeError(
                response,
                HttpStatus.FORBIDDEN,
                "You do not have permission to perform this action."
        );
    }
}
