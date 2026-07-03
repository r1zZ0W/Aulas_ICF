package mx.unam.icf.aulas.modules.reservations.students.app.exceptions;

import mx.unam.icf.aulas.kernel.infrastructure.exceptions.ResourceNotFoundException;

/**
 * Thrown when a student roster ({@code {groupUuid}.xlsx}) is requested (typically for
 * PDF export) but has not been uploaded yet, or the owning reservation group does not exist.
 *
 * <p>Extends {@link ResourceNotFoundException} so it is mapped to HTTP 404 by the existing
 * global exception handler without a new handler method.</p>
 *
 * @author Ithera
 * @version 1.0
 */
public class StudentListNotFoundException extends ResourceNotFoundException {

    public StudentListNotFoundException(String message) {
        super(message);
    }
}
