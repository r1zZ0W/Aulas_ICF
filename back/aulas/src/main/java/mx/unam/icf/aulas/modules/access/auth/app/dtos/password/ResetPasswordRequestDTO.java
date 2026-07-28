package mx.unam.icf.aulas.modules.access.auth.app.dtos.password;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequestDTO(
        @NotBlank(message = "FIELD_REQUIRED")
        String token,

        @NotBlank(message = "FIELD_REQUIRED")
        @Size(min = 8, max = 128, message = "FIELD_OUT_OF_RANGE")
        String newPassword
) {}
