package mx.unam.icf.aulas.modules.reservations.students.app.dtos;

/**
 * Response payload returned after a student roster is successfully uploaded and
 * the owning reservation group is confirmed.
 *
 * @param studentCount number of students parsed from the uploaded spreadsheet
 *
 * @author Ithera
 * @version 1.0
 */
public record StudentUploadResponseDTO(int studentCount) {}
