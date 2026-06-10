package mx.unam.icf.aulas.modules.resources.classrooms.app.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request payload for creating or updating a classroom.
 *
 * <p>Serves as both the create and update DTO since the fields are identical in both cases.
 * The {@code linkedRoomUuid} is optional and only set when two classrooms can be merged
 * into a larger space.</p>
 *
 * @param name           display name of the classroom
 * @param capacity       maximum number of students the classroom can accommodate
 * @param linkedRoomUuid public UUID of the paired classroom, or {@code null} if standalone
 * @param isActive       whether this classroom is available for reservations
 *
 * @author Ithera
 * @version 2.0
 */
public record ClassroomRequestDTO(

        @NotBlank(message = "Name is required")
        @Size(min = 1, max = 100, message = "Name must be between 1 and 100 characters")
        @Pattern(
                regexp = "^[\\w\\s\\-áéíóúÁÉÍÓÚñÑ\\.]+$",
                message = "Name contains invalid characters"
        )
        String name,

        @NotNull(message = "Capacity is required")
        @Min(value = 1, message = "Capacity must be at least 1")
        Long capacity,

        UUID linkedRoomUuid,
        Boolean isActive
) {}
