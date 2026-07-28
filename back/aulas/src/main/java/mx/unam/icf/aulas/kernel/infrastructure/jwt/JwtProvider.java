package mx.unam.icf.aulas.kernel.infrastructure.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.modules.access.users.infrastructure.userdetails.UserDetailsImp;
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
 *   <li>{@code sub}    – public user UUID</li>
 *   <li>{@code jti}    – unique token UUID for traceability and blacklisting</li>
 *   <li>{@code iss}    – issuer ("aulas-api")</li>
 *   <li>{@code aud}    – audience ("aulas-client")</li>
 *   <li>{@code uuid}   – public user UUID (same as sub, kept for client convenience)</li>
 *   <li>{@code nombre} – user's full name (auth tokens only)</li>
 *   <li>{@code role}   – role name e.g. "TEACHER", "ADMIN" (auth tokens only)</li>
 *   <li>{@code type}   – token purpose: "auth" | "refresh" | "reset"</li>
 *   <li>{@code ip}     – client IP at issuance (auth tokens only, for audit)</li>
 *   <li>{@code ua}     – User-Agent at issuance (auth tokens only, for audit)</li>
 *   <li>{@code iat}    – issued-at timestamp</li>
 *   <li>{@code exp}    – expiration timestamp</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class JwtProvider {

    private static final String ISSUER   = "aulas-api";
    private static final String AUDIENCE = "aulas-client";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    private SecretKey secretKey;

    private final HttpServletRequest request;

    /**
     * When true, X-Forwarded-For is trusted for client-IP resolution (audit claims).
     * Must only be enabled when the service runs behind a trusted reverse proxy.
     */
    @Value("${app.rate-limit.trust-proxy:false}")
    private boolean trustProxy;

    @PostConstruct
    private void initKey() {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // -------------------------------------------------------------------------
    // Token generation
    // -------------------------------------------------------------------------

    public String generateToken(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof UserDetailsImp principal))
            throw new IllegalArgumentException("Invalid or null principal");

        return buildToken(
                principal.getUuid().toString(),
                principal.getUuid().toString(),
                principal.getNombreCompleto(),
                principal.getRoleName(),
                "auth",
                expiration
        );
    }

    public String generateRefreshToken(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof UserDetailsImp principal))
            throw new IllegalArgumentException("Invalid or null principal");

        return buildToken(
                principal.getUuid().toString(),
                null, null, null,
                "refresh",
                refreshExpiration
        );
    }

    /**
     * Generates a short-lived password-reset token for the given user UUID.
     * Fixed 1-hour TTL regardless of the global expiration setting.
     */
    public String generateResetToken(String uuid) {
        return buildToken(uuid, null, null, null, "reset", 3_600_000L);
    }

    // -------------------------------------------------------------------------
    // Token reading
    // -------------------------------------------------------------------------

    public String getUuidFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    public String getJtiFromToken(String token) {
        return parseClaims(token).getId();
    }

    public String getTypeFromToken(String token) {
        return parseClaims(token).get("type", String.class);
    }

    /**
     * Extracts the {@code role} claim embedded in the JWT by the server at issuance time.
     *
     * <p>This value is cryptographically bound to the token signature: any client-side
     * modification (e.g., via localStorage or a forged header) invalidates the signature
     * and is rejected by {@link #isTokenInvalid} before this method is ever called.</p>
     *
     * @return the role name (e.g. "ADMIN", "TEACHER"), or {@code null} if the token type
     *         does not carry a role claim (refresh / reset tokens)
     */
    public String getRoleFromToken(String token) {
        return parseClaims(token).get("role", String.class);
    }

    public long getRemainingTtlSeconds(String token) {
        long expMs = parseClaims(token).getExpiration().getTime();
        long remaining = (expMs - System.currentTimeMillis()) / 1000;
        return Math.max(remaining, 0);
    }

    /**
     * Returns the currently authenticated user from the {@link SecurityContextHolder}.
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
     * Returns {@code true} when the token signature is invalid, the token is expired,
     * or the {@code type} claim is not a recognised value ("auth", "refresh", "reset").
     *
     * <p>IP/User-Agent binding has been removed — clients change networks legitimately
     * (e.g., Wi-Fi → 4G). The ip/ua claims remain in the token for audit purposes only.</p>
     */
    public boolean isTokenInvalid(String token) {
        try {
            Claims claims = parseClaims(token);
            String type = claims.get("type", String.class);
            return !"auth".equals(type) && !"refresh".equals(type) && !"reset".equals(type);
        } catch (Exception e) {
            return true;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String buildToken(String subject, String uuid, String name, String role,
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
        if (name   != null) builder.claim("name",   name);
        if (role   != null) builder.claim("role",   role);

        if ("auth".equals(type)) {
            builder.claim("ip", getClientIp());
            builder.claim("ua", request.getHeader("User-Agent"));
        }

        return builder.compact();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private String getClientIp() {
        if (trustProxy) {
            String xf = request.getHeader("X-Forwarded-For");
            if (xf != null && !xf.isBlank() && !"unknown".equalsIgnoreCase(xf)) {
                String[] parts = xf.split(",");
                return parts[parts.length - 1].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
