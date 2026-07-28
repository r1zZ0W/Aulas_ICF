package mx.unam.icf.aulas.modules.access.auth.app.dtos.refresh;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequestDTO(
        @NotBlank(message = "FIELD_REQUIRED")
        String refreshToken
) {}
