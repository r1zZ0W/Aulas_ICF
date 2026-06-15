package mx.unam.icf.aulas.modules.access.auth.app.exceptions;

/**
 * Thrown when an expected Authorization header or token body is absent or blank.
 * Mapped to HTTP 401 by {@code GlobalExceptionHandler}.
 */
public class MissingTokenException extends RuntimeException {

    public MissingTokenException(String message) {
        super(message);
    }

    public MissingTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
