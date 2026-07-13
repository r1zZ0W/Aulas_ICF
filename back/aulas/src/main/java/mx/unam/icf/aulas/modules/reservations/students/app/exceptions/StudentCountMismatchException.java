package mx.unam.icf.aulas.modules.reservations.students.app.exceptions;

import lombok.Getter;

/**
 * Thrown when the number of students in the uploaded roster does not exactly match the
 * {@code attendeeCount} declared in the booking request.
 *
 * <p>Raised by {@code ReservInstanceService.createBooking} <em>before</em> any database
 * write, so a mismatch never leaves a partial reservation. Mapped to HTTP 422 with a
 * structured {@code StudentCountMismatchDTO} payload (same family as
 * {@link EmptyStudentListException}/{@link DuplicateStudentException}) so the frontend
 * can render a message with both counts.</p>
 *
 * @author Ithera
 * @version 1.0
 */
@Getter
public class StudentCountMismatchException extends RuntimeException {

    /** Attendee count declared in the booking request. */
    private final int expected;

    /** Number of students actually parsed from the roster. */
    private final int actual;

    public StudentCountMismatchException(int expected, int actual) {
        super("The roster has " + actual + " students but the booking declares "
                + expected + " attendees.");
        this.expected = expected;
        this.actual = actual;
    }
}
