package mx.unam.icf.aulas.kernel.infrastructure.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import mx.unam.icf.aulas.kernel.infrastructure.web.responses.ApiResponse;
import org.jspecify.annotations.NonNull;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Per-request filter that extracts and validates the JWT from the {@code Authorization} header.
 *
 * <p>When a valid token is present the filter populates the {@link SecurityContextHolder},
 * allowing downstream security rules to identify the authenticated user.
 * If the token is present but invalid, the filter short-circuits with a 401 JSON response
 * instead of propagating the request unauthenticated.</p>
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider        jwtProvider;
    private final UserDetailsService userDetailsService;

    /**
     * Intercepts every HTTP request exactly once.
     * Requests without an {@code Authorization} header are passed through unmodified,
     * allowing Spring Security's own rules to decide whether they require authentication.
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         filterChain
    ) throws ServletException, IOException {

        String token = extractToken(request);

        if (StringUtils.hasText(token)) {
            if (!jwtProvider.validateToken(token)) {
                writeUnauthorized(response, "Invalid or expired token");
                return;
            }

            String email     = jwtProvider.getUsernameFromToken(token);
            UserDetails user = userDetailsService.loadUserByUsername(email);

            var auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the raw token string from the {@code Authorization: Bearer <token>} header.
     *
     * @param request the incoming HTTP request
     * @return the token string, or {@code null} if the header is absent or malformed
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer "))
            return header.substring(7);
        return null;
    }

    /**
     * Writes a structured 401 JSON error response and closes the filter chain early.
     * Uses the same {@link ApiResponse}
     * shape expected by the client.
     *
     * @param response the HTTP response to write to
     * @param message  the human-readable error message
     */
    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                "{\"error\":true,\"message\":\"" + message + "\",\"data\":null}"
        );
    }
}
