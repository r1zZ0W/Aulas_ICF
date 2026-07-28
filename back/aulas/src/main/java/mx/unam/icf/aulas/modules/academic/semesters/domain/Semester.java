package mx.unam.icf.aulas.modules.academic.semesters.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mx.unam.icf.aulas.kernel.domain.entities.BaseEntity;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Entity class representing an academic semester.
 *
 * <p>A semester defines an academic period during which classroom reservations may be scheduled.
 * Whether a semester is currently active is a <em>derived</em> concept computed by comparing
 * a caller-supplied reference date against {@link #startDate} and {@link #endDate} — it is not
 * persisted as a column. This keeps the entity pure and decoupled from the system clock,
 * which makes unit-testing straightforward.</p>
 *
 * @author Ithera
 * @version 2.0
 */
@Entity
@Table(name = "semesters")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class Semester extends BaseEntity {

    /**
     * Public UUID exposed through external APIs.
     * Not the primary key; never updated after creation.
     */
    @Column(name = "uuid", nullable = false, unique = true, updatable = false, columnDefinition = "BINARY(16)")
    private UUID uuid = UUID.randomUUID();

    /**
     * Unique human-readable semester identifier (e.g., {@code "2026-1"}).
     * Used as a display label and as a uniqueness constraint.
     */
    @Column(name = "name", nullable = false, unique = true)
    private String name;

    /**
     * First day of the semester (inclusive).
     * No reservation may be scheduled before this date for this semester.
     */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /**
     * Last day of the semester (inclusive).
     * No reservation may be scheduled after this date for this semester.
     */
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;
}

