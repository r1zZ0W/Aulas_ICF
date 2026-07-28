package mx.unam.icf.aulas.kernel.domain.exceptions;

import lombok.Getter;

/**
 * Generic exception for domain and application rule violations.
 * By default, this exception is mapped to HTTP 400 (Bad Request)
 * by the global exception handler.
 */
@Getter
public class DomainException extends RuntimeException {

    /** Stable, machine-readable identifier the frontend uses to render a localized message. */
    private final ErrorCode code;

    /**
     * Creates a new domain exception with a code and a custom message.
     *
     * @param code the stable error identifier the client resolves to a user-facing message
     * @param message the error message intended for logs/debugging (not shown to the user)
     */
    public DomainException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * Creates a new domain exception with a code, a custom message and a root cause.
     *
     * @param code the stable error identifier the client resolves to a user-facing message
     * @param message the error message intended for logs/debugging (not shown to the user)
     * @param cause the original exception that caused this error
     */
    public DomainException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

}
