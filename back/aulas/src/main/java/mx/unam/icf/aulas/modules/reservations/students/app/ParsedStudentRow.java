package mx.unam.icf.aulas.modules.reservations.students.app;

import mx.unam.icf.aulas.modules.reservations.students.domain.Student;

/**
 * A {@link Student} paired with the 1-based Excel row number it was read from.
 *
 * <p>Carrying the row number lets {@code StudentListService} report an accurate,
 * human-readable location ("fila 7") when it rejects a duplicate, matching the
 * business requirement to point out exactly where the problem is.</p>
 *
 * @param rowNumber 1-based row index in the source spreadsheet (header row is row 1)
 * @param student   the parsed student data for that row
 *
 * @author Ithera
 * @version 1.0
 */
public record ParsedStudentRow(int rowNumber, Student student) {}
