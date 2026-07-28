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

        @NotBlank(message = "FIELD_REQUIRED")
        @Size(min = 3, max = 50, message = "FIELD_OUT_OF_RANGE")
        String username,

        @NotBlank(message = "FIELD_REQUIRED")
        @Size(max = 128, message = "FIELD_OUT_OF_RANGE")
        String password
) {}
