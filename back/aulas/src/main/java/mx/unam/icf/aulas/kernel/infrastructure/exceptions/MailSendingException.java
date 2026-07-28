package mx.unam.icf.aulas.kernel.infrastructure.exceptions;

import mx.unam.icf.aulas.kernel.domain.exceptions.DomainException;
import mx.unam.icf.aulas.kernel.domain.exceptions.ErrorCode;

/**
 * Exception thrown when the application fails to send an email.
 */
public class MailSendingException extends DomainException {

    /**
     * Creates a new mail sending exception with a message.
     *
     * @param code the stable error identifier the client resolves to a user-facing message
     * @param message the error message (logs/debugging only)
     */
    public MailSendingException(ErrorCode code, String message) {
        super(code, message);
    }

    /**
     * Creates a new mail sending exception with a message and cause.
     *
     * @param code the stable error identifier the client resolves to a user-facing message
     * @param message the error message (logs/debugging only)
     * @param cause the original cause
     */
    public MailSendingException(ErrorCode code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
