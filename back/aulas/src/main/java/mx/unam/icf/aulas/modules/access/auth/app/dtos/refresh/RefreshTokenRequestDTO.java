package mx.unam.icf.aulas.modules.access.auth.app.dtos.refresh;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequestDTO(
        @NotBlank(message = "refreshToken is required")
        String refreshToken
) {}
