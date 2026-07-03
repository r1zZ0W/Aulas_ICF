package mx.unam.icf.aulas.modules.reservations.students.infrastructure;

import mx.unam.icf.aulas.modules.reservations.students.app.ParsedStudentRow;
import mx.unam.icf.aulas.modules.reservations.students.app.StudentExcelReader;
import mx.unam.icf.aulas.modules.reservations.students.app.exceptions.InvalidExcelFileException;
import mx.unam.icf.aulas.modules.reservations.students.domain.Student;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Apache POI ({@code .xlsx}) implementation of {@link StudentExcelReader}.
 *
 * <p>Reads defensively because real-world spreadsheets are messy: users move rows,
 * leave blank gaps, or clear cell content with the Delete key instead of deleting the
 * row entirely. Every cell is fetched with {@link MissingCellPolicy#RETURN_BLANK_AS_NULL}
 * and formatted through a {@link DataFormatter} so a numeric-typed or missing cell never
 * throws {@link IllegalStateException}/{@link NullPointerException} — it simply reads as
 * an empty string. Rows where all three target columns are blank are treated as
 * phantom rows and skipped; they do not count as roster data.</p>
 *
 * @author Ithera
 * @version 1.0
 */
@Component
public class PoiStudentExcelReader implements StudentExcelReader {

    private static final int COL_FIRST_NAME = 0;
    private static final int COL_LAST_NAME  = 1;
    private static final int COL_EMAIL      = 2;

    /** Row 0 holds column headers ("Nombre(s)", "Apellido(s)", "Correo electrónico"); data starts at row 1. */
    private static final int FIRST_DATA_ROW_INDEX = 1;

    @Override
    public List<ParsedStudentRow> read(byte[] xlsxContent) {
        DataFormatter formatter = new DataFormatter();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsxContent))) {
            Sheet sheet = workbook.getSheetAt(0);
            List<ParsedStudentRow> rows = new ArrayList<>();

            for (int rowIndex = FIRST_DATA_ROW_INDEX; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null)
                    continue; // phantom row: Excel counts it in getLastRowNum() but it has no cells at all

                String firstName = readCell(row, COL_FIRST_NAME, formatter);
                String lastName  = readCell(row, COL_LAST_NAME, formatter);
                String email     = readCell(row, COL_EMAIL, formatter);

                if (firstName.isBlank() && lastName.isBlank() && email.isBlank())
                    continue; // phantom row: cells were cleared with Delete, not a real removed row

                // 1-based row number as the user sees it in Excel (header = row 1).
                rows.add(new ParsedStudentRow(rowIndex + 1, new Student(firstName, lastName, email)));
            }

            return rows;
        } catch (InvalidExcelFileException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidExcelFileException("Could not read the uploaded file as a valid .xlsx workbook.", e);
        }
    }

    /**
     * Reads a cell as trimmed text, treating missing or blank cells as an empty string.
     * Never calls {@code getStringCellValue()} directly, so a numeric/date/formula cell
     * never throws {@link IllegalStateException}.
     */
    private String readCell(Row row, int columnIndex, DataFormatter formatter) {
        Cell cell = row.getCell(columnIndex, MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null)
            return "";
        return formatter.formatCellValue(cell).trim();
    }
}
