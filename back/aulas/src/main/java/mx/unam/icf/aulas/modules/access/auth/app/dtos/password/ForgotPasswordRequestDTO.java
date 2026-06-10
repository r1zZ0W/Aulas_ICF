package mx.unam.icf.aulas.modules.access.auth.app.dtos.password;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequestDTO(
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email
) {}
