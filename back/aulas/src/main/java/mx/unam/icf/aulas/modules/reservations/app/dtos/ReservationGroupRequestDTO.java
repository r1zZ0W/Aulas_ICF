package mx.unam.icf.aulas.modules.reservations.app.dtos;

import mx.unam.icf.aulas.modules.reservations.domain.ReservationGroupStatus;

import java.time.DayOfWeek;
import java.util.Set;
import java.util.UUID;

/**
 * Request payload for creating or updating a {@code ReservationGroup}.
 *
 * <p>A reservation group represents a recurring pattern of classroom reservations
 * owned by a user for a specific semester. The {@code daysOfWeek} set defines
 * on which weekdays the recurring pattern applies.</p>
 *
 * @param userUuid   public UUID of the user who owns this reservation group
 * @param semesterId internal identifier of the target semester
 * @param status     lifecycle status of the group
 * @param daysOfWeek weekdays on which this reservation pattern repeats
 *
 * @author Ithera
 * @version 2.0
 */
public record ReservationGroupRequestDTO(
        UUID userUuid,
        Long semesterId,
        ReservationGroupStatus status,
        Set<DayOfWeek> daysOfWeek
) {}
