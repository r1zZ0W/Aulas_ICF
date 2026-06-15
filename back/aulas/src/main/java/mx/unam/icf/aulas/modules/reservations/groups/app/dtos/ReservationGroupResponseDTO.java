package mx.unam.icf.aulas.modules.reservations.groups.app.dtos;

import mx.unam.icf.aulas.modules.reservations.groups.domain.ReservationGroupStatus;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Response payload exposing {@link mx.unam.icf.aulas.modules.reservations.groups.domain.ReservationGroup} data through the API.
 *
 * <p>Semester information is flattened to its name for client convenience,
 * and both the group and user are identified by their public UUIDs.</p>
 *
 * @param uuid         public UUID of the reservation group
 * @param userUuid     public UUID of the user who owns this group
 * @param semesterName name of the semester this group belongs to (e.g., "2026-1")
 * @param status       current lifecycle status of the group
 * @param daysOfWeek   weekdays on which this group repeats
 * @param createdAt    timestamp when this group record was created
 *
 * @author Ithera
 * @version 2.0
 */
public record ReservationGroupResponseDTO(
        UUID uuid,
        UUID userUuid,
        String semesterName,
        ReservationGroupStatus status,
        Set<DayOfWeek> daysOfWeek,
        LocalDateTime createdAt
) {}
