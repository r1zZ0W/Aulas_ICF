package mx.unam.icf.aulas.modules.resources.equipment.app.dtos;

import java.util.UUID;

/**
 * Response payload exposing an equipment resource through the API.
 *
 * <p>Uses the resource's public {@link UUID} as the external identifier;
 * the internal auto-generated {@code id} is never returned to clients.</p>
 *
 * @param uuid        public identifier of the resource
 * @param name        resource name
 * @param description optional human-readable description
 * @param quantity    total number of units of this equipment type in the catalog
 *
 * @author Ithera
 * @version 1.0
 */
public record ResourceResponseDTO(
        UUID uuid,
        String name,
        String description,
        Integer quantity
) {}
