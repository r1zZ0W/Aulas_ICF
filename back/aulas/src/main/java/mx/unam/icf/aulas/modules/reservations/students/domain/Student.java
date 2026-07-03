package mx.unam.icf.aulas.modules.reservations.students.domain;

/**
 * A single student entry parsed from a reservation group's roster (.xlsx).
 *
 * <p>Pure domain value — no Spring, Apache POI, or OpenPDF dependency. Excel columns map
 * as: {@code 0 = firstName}, {@code 1 = lastName}, {@code 2 = email}.</p>
 *
 * @param firstName student's given name(s) ("Nombre(s)")
 * @param lastName  student's family name(s) ("Apellido(s)")
 * @param email     student's institutional or personal email address
 *
 * @author Ithera
 * @version 1.0
 */
public record Student(String firstName, String lastName, String email) {

    /**
     * Normalized full-name key used to detect intra-file duplicates: lower-cased and
     * trimmed {@code firstName + "|" + lastName}. The email is intentionally excluded —
     * two rows with the same name but different emails still count as a duplicate
     * ("no repetir nombres").
     *
     * @return the deduplication key for this student
     */
    public String duplicateKey() {
        return firstName.trim().toLowerCase() + "|" + lastName.trim().toLowerCase();
    }

    /**
     * The display-ready full name ("firstName lastName"), used in the generated PDF.
     *
     * @return concatenated full name
     */
    public String fullName() {
        return firstName.trim() + " " + lastName.trim();
    }
}
