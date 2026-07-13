package mx.unam.icf.aulas.modules.reservations.students.app.dtos;

/**
 * Response payload for a single student in a reservation group's roster, returned by
 * the JSON student-list endpoint (as opposed to the PDF export).
 *
 * @param firstName student's given name(s) ("Nombre(s)")
 * @param lastName  student's family name(s) ("Apellido(s)")
 * @param email     student's institutional or personal email address
 *
 * @author Ithera
 * @version 1.0
 */
public record StudentResponseDTO(String firstName, String lastName, String email) {}
