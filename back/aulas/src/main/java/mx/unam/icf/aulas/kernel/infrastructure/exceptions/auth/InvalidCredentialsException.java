package mx.unam.icf.aulas.kernel.infrastructure.exceptions.auth;

/**
 * Thrown when the supplied email/password combination does not match any active account,
 * or when the credentials are structurally blank before a database lookup is even attempted.
 * Always mapped to HTTP 401 by {@code GlobalExceptionHandler} with a generic message
 * to prevent account enumeration.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }

    public InvalidCredentialsException(String message, Throwable cause) {
        super(message, cause);
    }
}
