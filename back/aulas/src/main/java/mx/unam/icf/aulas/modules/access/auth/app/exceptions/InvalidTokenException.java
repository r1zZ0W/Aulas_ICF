package mx.unam.icf.aulas.modules.access.auth.app.exceptions;

/**
 * Thrown when a JWT token fails signature verification, has expired, or carries
 * an unexpected {@code type} claim (e.g., using a refresh token where an auth token is required).
 * Mapped to HTTP 401 by {@code GlobalExceptionHandler}.
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
