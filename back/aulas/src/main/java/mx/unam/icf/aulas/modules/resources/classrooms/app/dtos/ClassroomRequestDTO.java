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

        @NotBlank(message = "FIELD_REQUIRED")
        @Size(min = 1, max = 100, message = "FIELD_OUT_OF_RANGE")
        @Pattern(
                regexp = "^[\\w\\s\\-áéíóúÁÉÍÓÚñÑ.]+$",
                message = "CLASSROOM_NAME_CHARSET_INVALID"
        )
        String name,

        @NotNull(message = "FIELD_REQUIRED")
        @Min(value = 2, message = "FIELD_OUT_OF_RANGE")
        Long capacity,

        @NotNull(message = "FIELD_REQUIRED")
        ClassroomType type,

        @Size(max = 500, message = "FIELD_OUT_OF_RANGE")
        String description,

        UUID linkedRoomUuid,
        Boolean isActive,

        @Size(max = 512, message = "FIELD_OUT_OF_RANGE")
        @URL(message = "CLASSROOM_IMAGE_URL_INVALID")
        String roomImageUrl
) {}
