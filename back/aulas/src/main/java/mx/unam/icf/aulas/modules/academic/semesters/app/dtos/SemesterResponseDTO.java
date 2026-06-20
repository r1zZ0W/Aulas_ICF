package mx.unam.icf.aulas.modules.academic.semesters.app.dtos;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response payload exposing {@link mx.unam.icf.aulas.modules.academic.semesters.domain.Semester}
 * data through the API.
 *
 * <p>{@code isActive} is a <em>derived</em> field: it is never persisted in the database.
 * The service layer computes it by comparing a reference date (typically today) against the
 * inclusive {@code [startDate, endDate]} range. Clients should treat this field as read-only
 * and never send it back in request payloads.</p>
 *
 * <p>Dates are serialised as ISO-8601 strings ({@code "yyyy-MM-dd"}).</p>
 *
 * @param uuid      public UUID of the semester; safe to expose in URLs and foreign references
 * @param name      unique human-readable label (e.g., {@code "2026-1"})
 * @param startDate first day of the semester (inclusive)
 * @param endDate   last day of the semester (inclusive)
 * @param isActive  {@code true} when the reference date falls within {@code [startDate, endDate]}
 * @param createdAt server timestamp of record creation
 * @param updatedAt server timestamp of the last update
 *
 * @author Ithera
 * @version 2.0
 */
public record SemesterResponseDTO(
        UUID uuid,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
