package mx.unam.icf.aulas.modules.reservations.students.app.exceptions;

/**
 * Thrown when the same student (by full name) appears more than once within a single
 * uploaded roster file. Aborts the entire upload: nothing is persisted or stored.
 *
 * <p>Carries the offending {@code row} and {@code studentFullName} so the global exception
 * handler can surface them as structured JSON (HTTP 422), the same pattern
 * {@link mx.unam.icf.aulas.modules.reservations.instances.app.exceptions.ReservationConflictException}
 * uses for the 409 booking-conflict response.</p>
 *
 * @author Ithera
 * @version 1.0
 */
public class DuplicateStudentException extends RuntimeException {

    private final int    row;
    private final String studentFullName;

    /**
     * @param row             1-based spreadsheet row where the duplicate was detected
     * @param studentFullName the duplicated student's full name
     */
    public DuplicateStudentException(int row, String studentFullName) {
        super("Duplicate student at row " + row + ": " + studentFullName);
        this.row = row;
        this.studentFullName = studentFullName;
    }

    /** Returns the 1-based row where the duplicate was found. */
    public int getRow() { return row; }

    /** Returns the duplicated student's full name. */
    public String getStudentFullName() { return studentFullName; }
}
