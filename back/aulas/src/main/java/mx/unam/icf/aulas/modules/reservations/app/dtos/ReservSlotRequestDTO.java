package mx.unam.icf.aulas.modules.reservations.app.dtos;

import java.time.LocalDate;

/**
 * Request payload for creating a {@code ReservSlot} booking.
 *
 * <p>Typically consumed internally by the reservation service when materializing
 * time-slot bookings from a confirmed instance. Not intended as a public create endpoint.
 * The {@code classroomId} and {@code userId} are stored as denormalized values for
 * fast conflict-detection queries.</p>
 *
 * @param instanceId  internal identifier of the owning reservation instance
 * @param timeSlotId  internal identifier of the time slot to book (1–24)
 * @param classroomId denormalized classroom identifier
 * @param userId      denormalized user identifier
 * @param date        date of the booking
 *
 * @author Ithera
 * @version 2.0
 */
public record ReservSlotRequestDTO(
        Long instanceId,
        Integer timeSlotId,
        Long classroomId,
        Long userId,
        LocalDate date
) {}
