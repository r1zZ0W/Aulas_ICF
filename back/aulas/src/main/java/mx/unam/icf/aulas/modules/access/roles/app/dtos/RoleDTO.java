package mx.unam.icf.aulas.modules.access.roles.app.dtos;

import java.time.LocalDateTime;

/**
 * Payload for transferring role catalog data across application layers.
 *
 * <p>Used as a response payload. Roles are predefined catalog entries
 * (e.g., {@code MAESTRO}, {@code ADMIN}) and are managed by administrators only.</p>
 *
 * @param id   internal numeric identifier (used as {@code roleId} in write requests)
 * @param name unique role name
 */
public record RoleDTO(
        Long id,
        String name
) {}
