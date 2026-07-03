package mx.unam.icf.aulas.modules.reservations.students.app.exceptions;

/**
 * Thrown when an uploaded roster file is structurally valid but contains no real
 * data rows (e.g. only the header row, or every row is blank).
 *
 * <p>Extends {@link RuntimeException} directly — like
 * {@link mx.unam.icf.aulas.modules.reservations.instances.app.exceptions.ReservationConflictException} —
 * rather than {@link mx.unam.icf.aulas.kernel.domain.exceptions.DomainException}, because it
 * maps to HTTP 422 (not the 400 used for generic business-rule violations) via a
 * dedicated handler in {@code GlobalExceptionHandler}.</p>
 *
 * @author Ithera
 * @version 1.0
 */
public class EmptyStudentListException extends RuntimeException {

    public EmptyStudentListException(String message) {
        super(message);
    }
}
