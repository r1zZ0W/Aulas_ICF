package mx.unam.icf.aulas.modules.resources.classrooms.app.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import mx.unam.icf.aulas.modules.resources.classrooms.domain.ClassroomType;
import org.hibernate.validator.constraints.URL;

import java.util.UUID;

public record ClassroomRequestDTO(

        @NotBlank(message = "Name is required")
        @Size(min = 1, max = 100, message = "Name must be between 1 and 100 characters")
        @Pattern(
                regexp = "^[\\w\\s\\-áéíóúÁÉÍÓÚñÑ.]+$",
                message = "Name contains invalid characters"
        )
        String name,

        @NotNull(message = "Capacity is required")
        @Min(value = 1, message = "Capacity must be at least 1")
        Long capacity,

        @NotNull(message = "Type is required")
        ClassroomType type,

        @Size(max = 500, message = "Description must be at most 500 characters")
        String description,

        UUID linkedRoomUuid,
        Boolean isActive,

        @Size(max = 512, message = "Image URL must be at most 512 characters")
        @URL(message = "Image URL must be a valid http(s) URL")
        String roomImageUrl
) {}
