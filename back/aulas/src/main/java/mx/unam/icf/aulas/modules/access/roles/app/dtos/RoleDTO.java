package mx.unam.icf.aulas.modules.access.roles.app.dtos;

import java.time.LocalDateTime;

/**
 * Payload for transferring role catalog data across application layers.
 *
 * <p>Used for both request and response payloads. Roles are predefined catalog entries
 * (e.g., {@code MAESTRO}, {@code ADMIN}) and are managed by administrators only.</p>
 *
 * @param name unique role name
 */
public record RoleDTO(
        String name
) {}
