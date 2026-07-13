package mx.unam.icf.aulas.modules.reservations.students.app.dtos;

/**
 * Structured payload of the HTTP 422 returned when the roster's student count does not
 * match the booking's declared attendee count, so the frontend can build a message with
 * both numbers ("El Excel tiene N alumnos pero indicaste M asistentes").
 *
 * @param expected attendee count declared in the booking request
 * @param actual   number of students actually parsed from the roster
 *
 * @author Ithera
 * @version 1.0
 */
public record StudentCountMismatchDTO(int expected, int actual) {}
