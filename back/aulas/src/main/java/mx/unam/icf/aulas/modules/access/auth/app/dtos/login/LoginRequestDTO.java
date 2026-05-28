package mx.unam.icf.aulas.modules.access.auth.app.dtos.login;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Payload expected by the {@code POST /api/v1/auth/login} endpoint.
 * Both fields are mandatory and the email must conform to a valid address format.
 */
public record LoginRequestDTO(

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format", regexp = "[^\\d]*@icf\\.unam\\.mx\n")
        String email,

        @NotBlank(message = "Password is required")
        String password
) {}
