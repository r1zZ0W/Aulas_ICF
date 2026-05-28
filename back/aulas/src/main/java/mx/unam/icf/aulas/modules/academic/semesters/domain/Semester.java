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

/**
 * Entity class representing an academic semester.
 *
 * This entity defines the semester period and its active status.
 *
 * @author Ithera
 * @version 1.0
 */
@Entity
@Table(name = "semesters")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class Semester extends BaseEntity {

    /**
     * Unique semester name (e.g., 2026-1).
     */
    @Column(name = "name", nullable = false, unique = true)
    private String name;

    /**
     * Semester start date.
     */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /**
     * Semester end date.
     */
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /**
     * Flag that indicates if the semester is active.
     */
    @Column(name = "is_active")
    private Boolean isActive;
}

