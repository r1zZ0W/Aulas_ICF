package mx.unam.icf.aulas.modules.access.auth.app.exceptions;

/**
 * Thrown when a user attempts to authenticate after their account has been temporarily
 * locked due to too many consecutive failed login attempts.
 *
 * <p>Mapped to HTTP 429 Too Many Requests by {@code GlobalExceptionHandler}.</p>
 *
 * @author Ithera
 * @version 1.0
 */
public class AccountLockedException extends RuntimeException {

    public AccountLockedException(String message) {
        super(message);
    }
}
