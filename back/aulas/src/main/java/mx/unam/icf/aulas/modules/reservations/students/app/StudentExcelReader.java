package mx.unam.icf.aulas.modules.reservations.students.app;

import java.util.List;

/**
 * Reads a student roster from raw {@code .xlsx} bytes.
 *
 * <p>Implementations must be defensive against real-world spreadsheets: blank cells,
 * phantom rows left by Excel's "Delete" key, and rows with only partial data. Rows
 * where every target column ({@code Nombre(s)}, {@code Apellido(s)}, {@code Correo})
 * is blank are silently skipped — they do not count as data.</p>
 *
 * <p>Assumes row 0 holds column headers; data starts at row 1 (1-based row numbers
 * reported in {@link ParsedStudentRow} therefore start at {@code 2}).</p>
 *
 * @author Ithera
 * @version 1.0
 */
public interface StudentExcelReader {

    /**
     * Parses every non-empty data row of the first sheet into a {@link ParsedStudentRow}.
     *
     * @param xlsxContent raw {@code .xlsx} file bytes (already magic-number validated by the caller)
     * @return parsed rows in spreadsheet order; empty when the sheet has no data rows
     * @throws mx.unam.icf.aulas.modules.reservations.students.app.exceptions.InvalidExcelFileException
     *         when the content cannot be parsed as a valid OOXML workbook
     */
    List<ParsedStudentRow> read(byte[] xlsxContent);
}
