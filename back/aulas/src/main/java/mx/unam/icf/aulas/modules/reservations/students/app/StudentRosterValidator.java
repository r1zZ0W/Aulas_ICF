package mx.unam.icf.aulas.modules.reservations.students.app;

import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.domain.exceptions.ErrorCode;
import mx.unam.icf.aulas.modules.reservations.students.app.exceptions.DuplicateStudentException;
import mx.unam.icf.aulas.modules.reservations.students.app.exceptions.EmptyStudentListException;
import mx.unam.icf.aulas.modules.reservations.students.app.exceptions.InvalidExcelFileException;
import mx.unam.icf.aulas.modules.reservations.students.domain.Student;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure, stateless validation of a student roster {@code .xlsx} file.
 *
 * <p>Extracted from {@code ReservationStudentService.upload} so the atomic booking flow
 * ({@code ReservInstanceService.createBooking}) can validate the mandatory roster
 * <em>before touching the database</em> without depending on the full upload service —
 * this component only needs {@link StudentExcelReader}, keeping the
 * instances↔students module coupling minimal.</p>
 *
 * <h3>Why {@code byte[]} and not {@code InputStream}</h3>
 * <p>The signature deliberately takes the full byte array: (a) POI's
 * {@code XSSFWorkbook(InputStream)} buffers the entire stream into memory anyway, so a
 * stream signature would not lower the RAM peak — only hide it; (b) the same bytes are
 * needed three times (magic-number check, POI parse, and the final
 * {@code FileStorageService.store} at the end of the booking transaction); (c) the size
 * is bounded by {@code spring.servlet.multipart.max-file-size=1MB}, so the array is
 * ≤1&nbsp;MB per request and the dominant memory cost is POI's DOM, identical in both
 * designs. The workbook itself is closed immediately via try-with-resources inside
 * {@code PoiStudentExcelReader}. If concurrent roster parsing ever became a real
 * bottleneck, the correct lever is SAX streaming ({@code XSSFReader}), not changing
 * this signature.</p>
 *
 * @author Ithera
 * @version 1.0
 */
@Component
@RequiredArgsConstructor
public class StudentRosterValidator {

    /** OOXML (.xlsx / .docx / .zip-based) magic number: {@code PK\x03\x04}. */
    private static final byte[] OOXML_MAGIC_NUMBER = {0x50, 0x4B, 0x03, 0x04};

    private final StudentExcelReader excelReader;

    /**
     * Runs the full roster validation pipeline and returns the parsed students.
     *
     * <p>Validation order (any failure aborts with the corresponding exception):</p>
     * <ol>
     *   <li>The file must start with the OOXML magic number.</li>
     *   <li>The file must parse as a workbook with at least one real data row.</li>
     *   <li>No two rows may share the same student full name.</li>
     * </ol>
     *
     * @param fileBytes raw {@code .xlsx} bytes as uploaded
     * @return the parsed students, in spreadsheet order
     * @throws InvalidExcelFileException when the file is not a valid {@code .xlsx} document
     * @throws EmptyStudentListException when the workbook has no real data rows
     * @throws DuplicateStudentException when the same student name appears twice
     */
    public List<Student> validate(byte[] fileBytes) {
        validateMagicNumber(fileBytes);

        List<ParsedStudentRow> rows = excelReader.read(fileBytes);
        if (rows.isEmpty())
            throw new EmptyStudentListException("The uploaded roster has no student rows.");

        return rejectDuplicatesOrCollect(rows);
    }

    /**
     * Confirms the first four bytes match the OOXML magic number without exhausting any
     * stream — the caller always hands over the full {@code byte[]} already read into memory
     * (from {@code MultipartFile#getBytes()}), so Apache POI can freely re-read it afterward
     * from a fresh {@code ByteArrayInputStream}.
     */
    private void validateMagicNumber(byte[] fileBytes) {
        if (fileBytes.length < OOXML_MAGIC_NUMBER.length)
            throw new InvalidExcelFileException(ErrorCode.ROSTER_FILE_INVALID, "The uploaded file is empty or too small to be a valid .xlsx document.");

        for (int i = 0; i < OOXML_MAGIC_NUMBER.length; i++) {
            if (fileBytes[i] != OOXML_MAGIC_NUMBER[i])
                throw new InvalidExcelFileException(ErrorCode.ROSTER_FILE_INVALID,
                        "The uploaded file is not a valid .xlsx (OOXML) document. " +
                        "Renamed .xls files and other formats are rejected.");
        }
    }

    /**
     * Detects intra-file duplicate students by full name using a {@link HashSet}, aborting
     * at the first collision with the offending row and name.
     */
    private List<Student> rejectDuplicatesOrCollect(List<ParsedStudentRow> rows) {
        Set<String> seenNames = new HashSet<>();
        List<Student> students = new ArrayList<>(rows.size());

        for (ParsedStudentRow row : rows) {
            Student student = row.student();
            if (!seenNames.add(student.duplicateKey()))
                throw new DuplicateStudentException(row.rowNumber(), student.fullName());
            students.add(student);
        }

        return students;
    }
}
