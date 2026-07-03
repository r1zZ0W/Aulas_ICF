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
 * The service automatically assigns {@code ACTIVA} status — clients must not send a status field.</p>
 *
 * @param groupUuid     public UUID of the {@link mx.unam.icf.aulas.modules.reservations.groups.domain.ReservationGroup} this instance belongs to
 * @param classroomUuid public UUID of the requested classroom
 * @param date          reservation date; must not be in the past
 * @param timeSlotIds   ordered list of 30-minute slot IDs (1–24, covering 07:00–19:00)
 * @param attendeeCount expected number of attendees
 * @param title         optional free-text label for this reservation; max 150 characters.
 *                      The frontend must {@code .trim()} the value before sending —
 *                      {@code @Size} validates the raw string, so 151 spaces would be
 *                      rejected with 400. The backend normalises blank/empty to {@code null}.
 *
 * @author Ithera
 * @version 2.1
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

        @NotNull(message = "Number of attendees is required")
        @Positive(message = "Number of attendees must be positive")
        Integer attendeeCount,

        @Size(max = 150, message = "Title must not exceed 150 characters")
        String title
) {}
