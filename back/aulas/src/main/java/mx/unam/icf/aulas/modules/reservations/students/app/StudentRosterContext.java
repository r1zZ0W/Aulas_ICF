package mx.unam.icf.aulas.modules.reservations.students.app;

/**
 * Display context passed to {@link StudentListPdfGenerator} so the generated PDF can
 * show an institutional title/subtitle without the generator needing to know about
 * {@code ReservationGroup} or any other JPA entity.
 *
 * @param groupLabel      human-readable label for the reservation group (e.g. classroom
 *                        name or a title, resolved by the caller)
 * @param teacherFullName full name of the teacher who owns the reservation group
 * @param totalStudents   total number of students in the roster
 *
 * @author Ithera
 * @version 1.0
 */
public record StudentRosterContext(String groupLabel, String teacherFullName, int totalStudents) {}
