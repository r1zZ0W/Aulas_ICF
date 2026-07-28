package mx.unam.icf.aulas.kernel.infrastructure.exceptions;

import mx.unam.icf.aulas.kernel.domain.exceptions.DomainException;
import mx.unam.icf.aulas.kernel.domain.exceptions.ErrorCode;

/**
 * Exception thrown when the application fails to write or read a file through
 * {@link mx.unam.icf.aulas.kernel.app.FileStorageService}.
 *
 * <p>Extends {@link DomainException} (like {@link MailSendingException}) so it is
 * still safely handled even if a caller forgets to catch it explicitly, but
 * {@link GlobalExceptionHandler} registers a dedicated handler that maps it to
 * HTTP 500 instead of the generic 400 used for business-rule violations.</p>
 */
public class FileStorageException extends DomainException {

    /**
     * Creates a new file storage exception with a message.
     *
     * @param code the stable error identifier the client resolves to a user-facing message
     * @param message the error message (logs/debugging only)
     */
    public FileStorageException(ErrorCode code, String message) {
        super(code, message);
    }

    /**
     * Creates a new file storage exception with a message and cause (typically an {@link java.io.IOException}).
     *
     * @param code the stable error identifier the client resolves to a user-facing message
     * @param message the error message (logs/debugging only)
     * @param cause   the original cause
     */
    public FileStorageException(ErrorCode code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
