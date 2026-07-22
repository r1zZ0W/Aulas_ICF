package mx.unam.icf.aulas.modules.resources.allocations.app.dtos;

import java.util.UUID;

/**
 * Response payload exposing a classroom–equipment allocation through the API.
 *
 * <p>Uses the public {@link UUID} of both the classroom and the equipment resource
 * as external identifiers, and flattens the equipment name to avoid unnecessary
 * nesting in the response.</p>
 *
 * @param classroomUuid public UUID of the classroom that owns this allocation
 * @param resourceUuid  public UUID of the equipment resource
 * @param resourceName  human-readable name of the equipment resource
 * @param quantity      number of units assigned to the classroom
 *
 * @author Ithera
 * @version 3.0
 */
public record ClassroomResourceResponseDTO(
        UUID classroomUuid,
        UUID resourceUuid,
        String resourceName,
        Integer quantity
) {}
