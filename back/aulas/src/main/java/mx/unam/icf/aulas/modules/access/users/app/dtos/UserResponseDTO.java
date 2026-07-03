package mx.unam.icf.aulas.modules.access.users.app.dtos;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response payload for user-related endpoints, representing a system user (teacher or administrator).
 *
 * @param uuid the unique identifier of the user, exposed as a UUID string
 * @param matricula
 * @param firstName
 * @param lastNames
 * @param username
 * @param email
 * @param extension
 * @param roleName
 * @param createdAt
 */
public record UserResponseDTO(
        UUID uuid,
        String institutionalId,
        String firstName,
        String lastNames,
        String username,
        String email,
        String extension,
        String roleName,
        LocalDateTime createdAt
) {}
