package mx.unam.icf.aulas.modules.access.users.app.dtos;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response payload exposing user data through the API.
 *
 * <p>Sensitive fields such as {@code passwordHash} are intentionally excluded.
 * The public {@link UUID} is used as the external identifier instead of the internal numeric ID.</p>
 *
 * @param uuid      public UUID used by external APIs
 * @param firstName first name of the user
 * @param lastNames last names of the user
 * @param email     email address of the user
 * @param roleName  name of the role assigned to this user (e.g., MAESTRO, ADMIN)
 * @param isActive  whether the user account is active
 * @param createdAt timestamp when this user record was created
 *
 * @author Ithera
 * @version 2.0
 */
public record UserResponseDTO(
        UUID uuid,
        String firstName,
        String lastNames,
        String email,
        String roleName,
        Boolean isActive,
        LocalDateTime createdAt
) {}
