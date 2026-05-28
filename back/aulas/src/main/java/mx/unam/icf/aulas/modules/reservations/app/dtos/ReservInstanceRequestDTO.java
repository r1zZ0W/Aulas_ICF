package mx.unam.icf.aulas.modules.reservations.app.dtos;

import mx.unam.icf.aulas.modules.reservations.domain.ReservInstanceStatus;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request payload for creating or updating a single {@code ReservInstance}.
 *
 * <p>A reservation instance represents one concrete occurrence of a recurring
 * reservation group on a specific date and classroom.</p>
 *
 * @param groupUuid     public UUID of the parent reservation group
 * @param classroomUuid public UUID of the classroom to be reserved
 * @param date          specific date for this reservation occurrence
 * @param status        lifecycle status of this occurrence
 *
 * @author Ithera
 * @version 2.0
 */
public record ReservInstanceRequestDTO(
        UUID groupUuid,
        UUID classroomUuid,
        LocalDate date,
        ReservInstanceStatus status
) {}
