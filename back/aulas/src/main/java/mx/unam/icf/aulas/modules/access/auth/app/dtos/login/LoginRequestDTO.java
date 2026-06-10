package mx.unam.icf.aulas.modules.access.auth.app.dtos.login;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload expected by the {@code POST /api/v1/auth/login} endpoint.
 *
 * <p>Fields are validated before reaching the service layer. Constraint violations
 * produce a structured {@code 400} response from {@code GlobalExceptionHandler},
 * preventing unnecessary database round-trips on obviously invalid input.</p>
 */
public record LoginRequestDTO(

        @NotBlank(message = "El nombre de usuario es obligatorio")
        @Size(min = 3, max = 50, message = "El nombre de usuario debe tener entre 3 y 50 caracteres")
        String username,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(max = 128, message = "La contraseña excede la longitud máxima permitida")
        String password
) {}
