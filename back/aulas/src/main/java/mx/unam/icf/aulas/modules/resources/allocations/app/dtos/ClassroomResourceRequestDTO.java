package mx.unam.icf.aulas.modules.resources.allocations.app.dtos;

/**
 * Request payload for creating or updating a classroom–equipment allocation.
 *
 * <p>Defines how many units of a specific equipment resource are available
 * in a given classroom. Both {@code classroomId} and {@code resourceId} must
 * reference existing entities.</p>
 *
 * @param classroomId internal identifier of the classroom
 * @param resourceId  internal identifier of the equipment resource
 * @param quantity    number of units of the equipment available in the classroom
 *
 * @author Ithera
 * @version 2.0
 */
public record ClassroomResourceRequestDTO(
        Long classroomId,
        Integer resourceId,
        Integer quantity
) {}
