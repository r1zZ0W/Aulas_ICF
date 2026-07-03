package mx.unam.icf.aulas.modules.reservations.students.app;

import mx.unam.icf.aulas.modules.reservations.students.domain.Student;

import java.util.List;

/**
 * Generates a PDF representation of a student roster, mirroring the source spreadsheet.
 *
 * @author Ithera
 * @version 1.0
 */
public interface StudentListPdfGenerator {

    /**
     * Builds a PDF document listing every student, entirely in memory.
     *
     * @param students the roster to render, in the order it should appear
     * @param context  display metadata (group label, teacher, total count)
     * @return PDF bytes ready to be sent as {@code application/pdf}
     */
    byte[] generate(List<Student> students, StudentRosterContext context);
}
