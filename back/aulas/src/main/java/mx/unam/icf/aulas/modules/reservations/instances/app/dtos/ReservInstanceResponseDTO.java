package mx.unam.icf.aulas.modules.reservations.instances.app.dtos;

import mx.unam.icf.aulas.modules.reservations.instances.domain.ReservInstanceStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response payload exposing a {@link mx.unam.icf.aulas.modules.reservations.instances.domain.ReservInstance} through the API.
 *
 * <p>Both the group and classroom are identified by their public UUIDs.
 * Audit timestamps are included for transparency in the admin interface.</p>
 *
 * @param uuid          public UUID of this reservation instance
 * @param groupUuid     public UUID of the parent reservation group
 * @param classroomUuid public UUID of the assigned classroom
 * @param date          date of this occurrence
 * @param status        current approval status
 * @param motivo        purpose or reason for the reservation
 * @param numAsistentes expected number of attendees
 * @param createdAt     timestamp when this record was created
 *
 * @author Ithera
 * @version 2.0
 */
public record ReservInstanceResponseDTO(
        UUID uuid,
        UUID groupUuid,
        UUID classroomUuid,
        LocalDate date,
        ReservInstanceStatus status,
        String motivo,
        Integer numAsistentes,
        LocalDateTime createdAt
) {}
