package mx.unam.icf.aulas.kernel.infrastructure.exceptions;

import mx.unam.icf.aulas.kernel.domain.exceptions.DomainException;

/**
 * Exception thrown when the application fails to send an email.
 */
public class MailSendingException extends DomainException {

    /**
     * Creates a new mail sending exception with a message.
     *
     * @param message the error message
     */
    public MailSendingException(String message) {
        super(message);
    }

    /**
     * Creates a new mail sending exception with a message and cause.
     *
     * @param message the error message
     * @param cause the original cause
     */
    public MailSendingException(String message, Throwable cause) {
        super(message, cause);
    }
}
