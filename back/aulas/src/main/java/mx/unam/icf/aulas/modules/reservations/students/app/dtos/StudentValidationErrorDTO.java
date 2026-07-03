package mx.unam.icf.aulas.modules.reservations.students.app.dtos;

/**
 * Structured payload returned as the {@code data} field of a 422 Unprocessable Entity
 * response when a student roster upload fails business validation.
 *
 * <p>Mirrors {@code ConflictDetailDTO}'s role for the 409 booking-conflict response:
 * lets the frontend show a precise message (e.g. "Alumno duplicado en la fila 7: Juan
 * Pérez") instead of a generic error notice.</p>
 *
 * @param row   1-based spreadsheet row where the problem was detected;
 *              {@code null} when the error is not row-specific (e.g. an empty roster)
 * @param value the offending value (e.g. the duplicated student's full name);
 *              {@code null} when not applicable
 *
 * @author Ithera
 * @version 1.0
 */
public record StudentValidationErrorDTO(Integer row, String value) {}
