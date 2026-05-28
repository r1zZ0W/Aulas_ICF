package mx.unam.icf.aulas.modules.academic.semesters.app.dtos;

import java.time.LocalDate;

/**
 * Payload for transferring semester catalog data across application layers.
 *
 * <p>Semesters define the academic periods during which classroom reservations are active.
 * Only one semester should have {@code isActive = true} at a time.</p>
 *
 * @param name      unique semester name (e.g., "2026-1")
 * @param startDate first day of the semester (inclusive)
 * @param endDate   last day of the semester (inclusive)
 * @param isActive  whether this semester is currently the active academic period
 *
 * @author Ithera
 * @version 2.0
 */
public record SemesterDTO(
        String name,
        LocalDate startDate,
        LocalDate endDate,
        Boolean isActive
) {}
