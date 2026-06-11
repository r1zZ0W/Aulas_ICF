package mx.unam.icf.aulas.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.infrastructure.web.FilterResponseWriter;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Returns HTTP 401 when a request reaches a protected route without a valid JWT.
 *
 * <p>Spring Security invokes this entry point when the filter chain determines the request
 * is not authenticated (no token, expired token that slipped past the JWT filter, etc.).
 * Without this bean the framework falls back to its built-in HTML redirect, which breaks
 * API clients expecting JSON.</p>
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final FilterResponseWriter filterResponseWriter;

    @Override
    public void commence(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                         @NonNull AuthenticationException authException) throws IOException {
        filterResponseWriter.writeError(
                response,
                HttpStatus.UNAUTHORIZED,
                "Authentication required. Provide a valid Bearer token."
        );
    }
}
