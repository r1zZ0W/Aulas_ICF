package mx.unam.icf.aulas.kernel.infrastructure.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import mx.unam.icf.aulas.kernel.infrastructure.web.FilterResponseWriter;
import mx.unam.icf.aulas.modules.access.users.infrastructure.userdetails.UserDetailsServiceImpl;
import org.jspecify.annotations.NonNull;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Per-request filter that extracts and validates the JWT from the {@code Authorization} header.
 *
 * <p>Only tokens with {@code type="auth"} are accepted for API access. Refresh and reset tokens
 * are rejected here — they are validated explicitly in their respective service methods.
 * Revoked tokens (blacklisted by jti in Redis) are also rejected with 401.</p>
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider            jwtProvider;
    private final UserDetailsServiceImpl userDetailsService;
    private final TokenBlacklist          blacklistService;
    private final FilterResponseWriter   filterResponseWriter;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         filterChain
    ) throws ServletException, IOException {

        String token = extractToken(request);

        if (StringUtils.hasText(token)) {
            if (jwtProvider.isTokenInvalid(token)) {
                filterResponseWriter.writeError(response, HttpStatus.UNAUTHORIZED, "Invalid or expired token");
                return;
            }

            if (!"auth".equals(jwtProvider.getTypeFromToken(token))) {
                filterResponseWriter.writeError(response, HttpStatus.UNAUTHORIZED, "Invalid token type");
                return;
            }

            String jti = jwtProvider.getJtiFromToken(token);
            if (blacklistService.isBlacklisted(jti)) {
                filterResponseWriter.writeError(response, HttpStatus.UNAUTHORIZED, "Token has been revoked");
                return;
            }

            String uuid = jwtProvider.getUuidFromToken(token);
            String role = jwtProvider.getRoleFromToken(token);

            // Load user ONLY to verify the account is still active.
            // The role/authorities come from the signed JWT claim below, never from the
            // client or from a separate header the frontend could forge via localStorage.
            UserDetails user = userDetailsService.loadUserByUuid(uuid);
            if (!user.isEnabled()) {
                filterResponseWriter.writeError(response, HttpStatus.UNAUTHORIZED, "Account is disabled");
                return;
            }

            // Build authorities from the JWT 'role' claim.
            // The HMAC signature guarantees this value was written by the server at login time.
            List<GrantedAuthority> authorities = (role != null)
                    ? List.of(new SimpleGrantedAuthority("ROLE_" + role))
                    : Collections.emptyList();

            var auth = new UsernamePasswordAuthenticationToken(user, null, authorities);
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer "))
            return header.substring(7);
        return null;
    }
}
