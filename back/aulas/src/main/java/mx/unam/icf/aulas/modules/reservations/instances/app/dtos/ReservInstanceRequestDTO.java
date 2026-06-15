package mx.unam.icf.aulas.modules.reservations.instances.app.dtos;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request payload for creating a classroom reservation instance.
 *
 * <p>Carries the classroom, date, time slots, purpose, and expected attendee count.
 * The service automatically assigns {@code PENDIENTE} status — clients must not send a status field.</p>
 *
 * @param groupUuid     public UUID of the {@link mx.unam.icf.aulas.modules.reservations.groups.domain.ReservationGroup} this instance belongs to
 * @param classroomUuid public UUID of the requested classroom
 * @param date          reservation date; must not be in the past
 * @param timeSlotIds   ordered list of 30-minute slot IDs (1–24, covering 07:00–19:00)
 * @param motivo        purpose or reason for the reservation (max 500 chars)
 * @param numAsistentes expected number of attendees
 *
 * @author Ithera
 * @version 2.0
 */
public record ReservInstanceRequestDTO(

        @NotNull(message = "Group UUID is required")
        UUID groupUuid,

        @NotNull(message = "Classroom UUID is required")
        UUID classroomUuid,

        @NotNull(message = "Reservation date is required")
        @FutureOrPresent(message = "Reservation date must not be in the past")
        LocalDate date,

        @NotEmpty(message = "At least one time slot is required")
        List<Integer> timeSlotIds,

        @NotNull(message = "Purpose (motivo) is required")
        @Size(max = 500, message = "Purpose must be at most 500 characters")
        String motivo,

        @NotNull(message = "Number of attendees is required")
        @Positive(message = "Number of attendees must be positive")
        Integer numAsistentes
) {}
