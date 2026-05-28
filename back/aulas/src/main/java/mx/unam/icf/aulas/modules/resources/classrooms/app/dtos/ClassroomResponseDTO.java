package mx.unam.icf.aulas.modules.resources.classrooms.app.dtos;

import java.util.UUID;

/**
 * Response payload exposing classroom data through the API.
 *
 * <p>Uses the public {@link java.util.UUID} as the external identifier.
 * The {@code linkedRoomUuid} field is populated only when two classrooms are configured
 * as a paired space.</p>
 *
 * @param uuid           public UUID used by external APIs
 * @param name           display name of the classroom
 * @param capacity       maximum student capacity
 * @param linkedRoomUuid public UUID of the paired classroom, or {@code null} if standalone
 * @param isActive       whether this classroom is available for reservations
 *
 * @author Ithera
 * @version 2.0
 */
public record ClassroomResponseDTO(
        UUID uuid,
        String name,
        Long capacity,
        UUID linkedRoomUuid,
        Boolean isActive
) {}
