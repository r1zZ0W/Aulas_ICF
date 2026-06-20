package mx.unam.icf.aulas.modules.academic.semesters.app.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Request payload for creating or updating a {@link mx.unam.icf.aulas.modules.academic.semesters.domain.Semester}.
 *
 * <p>Date ordering and the no-past rule are <em>not</em> enforced here via Bean Validation
 * annotations (e.g., {@code @FutureOrPresent}) because the strictness of those rules differs
 * between create and update operations. All cross-field date validations are handled in the
 * service layer, which can inspect existing persisted state.</p>
 *
 * <p>Dates must be supplied as ISO-8601 strings ({@code "yyyy-MM-dd"}).</p>
 *
 * @param name      unique semester identifier (e.g., {@code "2026-1"})
 * @param startDate first day of the semester (inclusive); must not be null
 * @param endDate   last day of the semester (inclusive); must be after {@code startDate}
 *
 * @author Ithera
 * @version 2.0
 */
public record SemesterRequestDTO(

        @NotBlank(message = "Semester name is required")
        String name,

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        @NotNull(message = "End date is required")
        LocalDate endDate
) {}
