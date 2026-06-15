package mx.unam.icf.aulas.modules.access.users.app.dtos;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponseDTO(
        UUID uuid,
        String firstName,
        String lastNames,
        String username,
        String email,
        String roleName,
        Boolean isActive,
        LocalDateTime createdAt
) {}
