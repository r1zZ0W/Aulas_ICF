package mx.unam.icf.aulas.modules.reservations.app.dtos;

import mx.unam.icf.aulas.modules.reservations.domain.ReservInstanceStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response payload exposing a {@code ReservInstance} occurrence through the API.
 *
 * <p>All entity references are expressed as public UUIDs to avoid leaking internal IDs.
 * The {@code createdAt} field reflects when the record was first persisted.</p>
 *
 * @param uuid          public UUID of this reservation instance
 * @param groupUuid     public UUID of the parent reservation group
 * @param classroomUuid public UUID of the reserved classroom
 * @param date          date of this occurrence
 * @param status        current lifecycle status of this occurrence
 * @param createdAt     timestamp when this instance record was created
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
        LocalDateTime createdAt
) {}
