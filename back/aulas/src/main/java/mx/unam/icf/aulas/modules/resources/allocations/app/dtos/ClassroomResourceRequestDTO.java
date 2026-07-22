package mx.unam.icf.aulas.modules.resources.allocations.app.dtos;

import java.util.UUID;

/**
 * Request payload for creating or updating a classroom–equipment allocation.
 *
 * <p>Defines how many units of a specific equipment resource are assigned to a
 * classroom. The classroom is identified by the path variable of the enclosing
 * endpoint ({@code /api/v1/classrooms/{classroomUuid}/resources}), never by a
 * field in this body. The resource is identified by its public {@link #resourceUuid},
 * consistent with the UUID-only identification used across the API.</p>
 *
 * @param resourceUuid public UUID of the equipment resource
 * @param quantity     number of units of the equipment assigned to the classroom
 *
 * @author Ithera
 * @version 3.0
 */
public record ClassroomResourceRequestDTO(
        UUID resourceUuid,
        Integer quantity
) {}
