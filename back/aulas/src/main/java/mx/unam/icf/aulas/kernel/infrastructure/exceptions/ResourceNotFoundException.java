package mx.unam.icf.aulas.kernel.infrastructure.exceptions;

import mx.unam.icf.aulas.kernel.domain.exceptions.DomainException;
import mx.unam.icf.aulas.kernel.domain.exceptions.ErrorCode;

/**
 * Exception thrown when a requested resource cannot be found.
 * <p>
 * This exception is typically translated to HTTP 404 (Not Found).
 */
public class ResourceNotFoundException extends DomainException {

    /**
     * Creates a new resource-not-found exception.
     *
     * @param code the stable error identifier the client resolves to a user-facing message
     * @param message a descriptive message about the missing resource (logs/debugging only)
     */
    public ResourceNotFoundException(ErrorCode code, String message) {
        super(code, message);
    }
}
