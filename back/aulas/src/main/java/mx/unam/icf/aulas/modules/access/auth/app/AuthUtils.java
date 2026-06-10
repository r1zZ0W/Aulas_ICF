package mx.unam.icf.aulas.modules.access.auth.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.unam.icf.aulas.kernel.infrastructure.exceptions.auth.InvalidCredentialsException;
import mx.unam.icf.aulas.kernel.infrastructure.exceptions.auth.MissingTokenException;
import mx.unam.icf.aulas.kernel.infrastructure.jwt.JwtProvider;
import mx.unam.icf.aulas.kernel.infrastructure.jwt.TokenBlacklistService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

/**
 * Small authentication helpers shared by the service layer.
 * Keeps parsing and security-side effects in one place.
 */
@Slf4j
@RequiredArgsConstructor
@Component
class AuthUtils {

    /** Generic message returned on any credential failure — avoids account enumeration. */
    private static final String INVALID_CREDENTIALS_MSG = "Invalid credentials.";

    private final AuthenticationManager authenticationManager;
    private final JwtProvider           jwtProvider;
    private final TokenBlacklistService blacklistService;

    /**
     * Extracts the raw token from a Bearer header value and validates it is present.
     *
     * @param authHeader Authorization header or raw token
     * @return raw token string
     */
    public String extractBearerToken(String authHeader) {
        if (authHeader == null || authHeader.isBlank())
            throw new MissingTokenException("Authorization header is missing or empty");
        return authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
    }

    /**
     * Authenticates credentials using the configured AuthenticationManager.
     *
     * <p>All authentication failures — whether the account does not exist, the password
     * is wrong, or the account is disabled — are mapped to the same neutral message to
     * prevent user-enumeration attacks.</p>
     *
     * @param email    user email
     * @param password user password
     * @return authenticated {@link Authentication}
     * @throws InvalidCredentialsException always with a neutral message on any auth failure
     */
    public Authentication authenticate(String username, String password) {
        try {
            return authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );
        } catch (AuthenticationException e) {
            log.warn("Authentication failed for username={} reason={}", username, e.getClass().getSimpleName());
            throw new InvalidCredentialsException(INVALID_CREDENTIALS_MSG);
        }
    }

    /**
     * Adds a token to the blacklist when possible; ignores malformed or expired tokens.
     *
     * @param token raw token string
     */
    public void safeBlacklist(String token) {
        try {
            blacklistService.blacklist(
                    jwtProvider.getJtiFromToken(token),
                    jwtProvider.getRemainingTtlSeconds(token)
            );
        } catch (Exception ignored) {
            // Token already expired or malformed — nothing to revoke
        }
    }

    /**
     * Fast-fail guard executed before any I/O on login.
     * Throws a neutral exception on blank credentials to prevent timing-based probing
     * and avoid unnecessary database calls.
     */
    public void guardCredentials(String username, String password) {
        if (username == null || username.isBlank()
                || password == null || password.isBlank()) {
            throw new InvalidCredentialsException(INVALID_CREDENTIALS_MSG);
        }
    }
}
