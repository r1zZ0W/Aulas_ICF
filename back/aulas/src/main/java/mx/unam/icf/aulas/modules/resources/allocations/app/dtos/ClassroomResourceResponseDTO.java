package mx.unam.icf.aulas.modules.resources.allocations.app.dtos;

import java.util.UUID;

/**
 * Response payload exposing a classroom–equipment allocation through the API.
 *
 * <p>Uses the classroom's public {@link UUID} as the external identifier and
 * flattens the equipment name to avoid unnecessary nesting in the response.</p>
 *
 * @param classroomUuid public UUID of the classroom that owns this allocation
 * @param resourceId    internal identifier of the equipment resource
 * @param resourceName  human-readable name of the equipment resource
 * @param quantity      number of units available in the classroom
 *
 * @author Ithera
 * @version 2.0
 */
public record ClassroomResourceResponseDTO(
        UUID classroomUuid,
        Integer resourceId,
        String resourceName,
        Integer quantity
) {}
