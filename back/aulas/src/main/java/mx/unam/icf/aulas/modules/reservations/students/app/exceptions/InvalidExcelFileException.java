package mx.unam.icf.aulas.modules.reservations.students.app.exceptions;

import mx.unam.icf.aulas.kernel.domain.exceptions.DomainException;
import mx.unam.icf.aulas.kernel.domain.exceptions.ErrorCode;

/**
 * Thrown when an uploaded roster file fails the OOXML magic-number check
 * ({@code PK\x03\x04}) or cannot otherwise be parsed as a valid {@code .xlsx} workbook
 * (renamed {@code .xls}, corrupted file, unsupported format, etc.).
 *
 * <p>Extends {@link DomainException} so it is mapped to HTTP 400 by the existing
 * global exception handler without a new handler method.</p>
 *
 * @author Ithera
 * @version 1.0
 */
public class InvalidExcelFileException extends DomainException {

    public InvalidExcelFileException(ErrorCode code, String message) {
        super(code, message);
    }

    public InvalidExcelFileException(ErrorCode code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
