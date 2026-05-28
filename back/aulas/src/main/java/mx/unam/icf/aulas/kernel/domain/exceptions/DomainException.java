package mx.unam.icf.aulas.kernel.domain.exceptions;

/**
 * Generic exception for domain and application rule violations.
 * By default, this exception is mapped to HTTP 400 (Bad Request)
 * by the global exception handler.
 */
public class DomainException extends RuntimeException {

    /**
     * Creates a new domain exception with a custom message.
     *
     * @param message the error message intended for the client
     */
    public DomainException(String message) {
        super(message);
    }

    /**
     * Creates a new domain exception with a custom message and root cause.
     *
     * @param message the error message intended for the client
     * @param cause the original exception that caused this error
     */
    public DomainException(String message, Throwable cause) {
        super(message, cause);
    }

}
