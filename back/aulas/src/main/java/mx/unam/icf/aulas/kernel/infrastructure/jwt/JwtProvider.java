package mx.unam.icf.aulas.kernel.infrastructure.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import mx.unam.icf.aulas.modules.access.users.infrastructure.UserDetailsImp;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Utility component for generating and validating JWT tokens.
 *
 * <p>Authentication token payload claims:</p>
 * <ul>
 *   <li>{@code sub}    – user email (session identifier)</li>
 *   <li>{@code jti}    – unique token UUID for traceability</li>
 *   <li>{@code iss}    – issuer ("aulas-api")</li>
 *   <li>{@code aud}    – audience ("aulas-client")</li>
 *   <li>{@code uuid}   – public user UUID (internal numeric ID is never exposed)</li>
 *   <li>{@code nombre} – user's full name</li>
 *   <li>{@code role}   – role name (e.g. "MAESTRO", "ADMIN")</li>
 *   <li>{@code type}   – token purpose: "auth" | "reset"</li>
 *   <li>{@code ip}     – client IP at the time of issuance (auth tokens only)</li>
 *   <li>{@code ua}     – User-Agent at the time of issuance (auth tokens only)</li>
 *   <li>{@code iat}    – issued-at timestamp</li>
 *   <li>{@code exp}    – expiration timestamp</li>
 * </ul>
 */
@Component
public class JwtProvider {

    private static final String ISSUER   = "aulas-api";
    private static final String AUDIENCE = "aulas-client";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private SecretKey secretKey;

    private final HttpServletRequest request;

    public JwtProvider(HttpServletRequest request) {
        this.request = request;
    }

    /** Derives the HMAC-SHA secret key from the configured secret string after bean initialization. */
    @PostConstruct
    private void initKey() {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // -------------------------------------------------------------------------
    // Token generation
    // -------------------------------------------------------------------------

    /**
     * Generates a signed authentication JWT for the given authenticated principal.
     *
     * @param authentication a fully authenticated Spring Security context
     * @return compact signed JWT string
     * @throws IllegalArgumentException if the principal is not a {@link UserDetailsImp} instance
     */
    public String generateToken(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof UserDetailsImp principal))
            throw new IllegalArgumentException("Invalid or null principal");

        return buildToken(
                principal.getUsername(),
                principal.getUuid().toString(),
                principal.getNombreCompleto(),
                principal.getRoleName(),
                "auth",
                expiration
        );
    }

    /**
     * Generates a short-lived password-reset token containing only the user's email.
     * The token has a fixed 1-hour TTL regardless of the global expiration setting.
     *
     * @param email the email address of the user requesting the reset
     * @return compact signed JWT string with {@code type="reset"}
     */
    public String generateResetToken(String email) {
        return buildToken(email, null, null, null, "reset", 3_600_000L);
    }

    // -------------------------------------------------------------------------
    // Token reading
    // -------------------------------------------------------------------------

    /**
     * Extracts the subject (user email) from a valid, signed token.
     *
     * @param token compact JWT string
     * @return the email stored in the {@code sub} claim
     */
    public String getUsernameFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Returns the currently authenticated user from the {@link SecurityContextHolder}.
     * Avoids an additional database round-trip by reading the principal already set by the filter.
     *
     * @return the authenticated {@link UserDetailsImp}, or empty if no session is active
     */
    public Optional<UserDetailsImp> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetailsImp user)
            return Optional.of(user);
        return Optional.empty();
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    /**
     * Validates signature, expiration, token type, and (for auth tokens) IP and User-Agent binding.
     *
     * @param token compact JWT string to validate
     * @return {@code true} if the token is valid and matches the current request context
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = parseClaims(token);

            String type = claims.get("type", String.class);
            if (!"auth".equals(type) && !"reset".equals(type)) return false;

            // Auth tokens are bound to the IP and User-Agent recorded at issuance
            if ("auth".equals(type)) {
                String tokenIp = claims.get("ip", String.class);
                if (tokenIp != null && !tokenIp.equals(getClientIp())) return false;

                String tokenUa = claims.get("ua", String.class);
                if (tokenUa != null && !tokenUa.equals(request.getHeader("User-Agent"))) return false;
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Builds and signs a JWT with the provided claims.
     * Optional claims ({@code uuid}, {@code nombre}, {@code role}) are omitted when {@code null}.
     * IP and User-Agent are embedded only for {@code type="auth"} tokens.
     */
    private String buildToken(String subject, String uuid, String nombre, String role,
                              String type, long ttlMs) {
        Date now = new Date();

        var builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .subject(subject)
                .claim("type", type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMs))
                .signWith(secretKey);

        if (uuid   != null) builder.claim("uuid",   uuid);
        if (nombre != null) builder.claim("nombre", nombre);
        if (role   != null) builder.claim("role",   role);

        if ("auth".equals(type)) {
            builder.claim("ip", getClientIp());
            builder.claim("ua", request.getHeader("User-Agent"));
        }

        return builder.compact();
    }

    /** Parses and verifies the token signature, returning the claims payload. */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Resolves the real client IP, honoring the {@code X-Forwarded-For} header
     * when the application runs behind a reverse proxy.
     */
    private String getClientIp() {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank() && !"unknown".equalsIgnoreCase(xf))
            return xf.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
