package mx.unam.icf.aulas.modules.reservations.app.dtos;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Response payload exposing a {@code ReservSlot} booking through the API.
 *
 * <p>Time-slot details ({@code startTime}, {@code endTime}) are flattened from the
 * related {@code TimeSlot} entity to avoid unnecessary nesting.
 * The owning instance is identified by its public UUID.</p>
 *
 * @param instanceUuid public UUID of the owning reservation instance
 * @param timeSlotId   identifier of the booked time slot (1–24)
 * @param startTime    start time of the booked slot (inclusive)
 * @param endTime      end time of the booked slot (exclusive)
 * @param classroomId  denormalized classroom identifier
 * @param userId       denormalized user identifier
 * @param date         date of the booking
 *
 * @author Ithera
 * @version 2.0
 */
public record ReservSlotResponseDTO(
        UUID instanceUuid,
        Integer timeSlotId,
        LocalTime startTime,
        LocalTime endTime,
        Long classroomId,
        Long userId,
        LocalDate date
) {}
