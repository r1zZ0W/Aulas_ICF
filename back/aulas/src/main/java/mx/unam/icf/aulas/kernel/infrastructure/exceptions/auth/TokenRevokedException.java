package mx.unam.icf.aulas.kernel.infrastructure.exceptions.auth;

/**
 * Thrown when a token's JTI (unique token ID) is found in the Redis blacklist,
 * indicating it was explicitly revoked via logout, token rotation, or a password reset.
 * Mapped to HTTP 401 by {@code GlobalExceptionHandler}.
 */
public class TokenRevokedException extends RuntimeException {

    public TokenRevokedException(String message) {
        super(message);
    }

    public TokenRevokedException(String message, Throwable cause) {
        super(message, cause);
    }
}
