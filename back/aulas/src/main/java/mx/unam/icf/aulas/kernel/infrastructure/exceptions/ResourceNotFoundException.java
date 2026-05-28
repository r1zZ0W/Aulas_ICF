package mx.unam.icf.aulas.kernel.infrastructure.exceptions;

import mx.unam.icf.aulas.kernel.domain.exceptions.DomainException;

/**
 * Exception thrown when a requested resource cannot be found.
 * <p>
 * This exception is typically translated to HTTP 404 (Not Found).
 */
public class ResourceNotFoundException extends DomainException {

    /**
     * Creates a new resource-not-found exception.
     *
     * @param message a descriptive message about the missing resource
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
