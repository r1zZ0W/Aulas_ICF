package mx.unam.icf.aulas.modules.access.roles.app.dtos;

import java.time.LocalDateTime;

/**
 * Payload for transferring role catalog data across application layers.
 *
 * <p>Used for both request and response payloads. Roles are catalog entries
 * with predefined names such as {@code MAESTRO} and {@code ADMIN}.</p>
 *
 * @param name        unique role name
 * @param description optional human-readable description of the role
 * @param createdAt   timestamp when the role was created
 *
 * @author Ithera
 * @version 2.0
 */
public record RoleDTO(
        String name,
        String description,
        LocalDateTime createdAt
) {}
