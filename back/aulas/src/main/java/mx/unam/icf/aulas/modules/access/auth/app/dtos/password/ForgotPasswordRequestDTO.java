package mx.unam.icf.aulas.modules.access.auth.app.dtos.password;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequestDTO(
        @NotBlank(message = "FIELD_REQUIRED")
        @Email(message = "FIELD_INVALID_FORMAT")
        String email
) {}
