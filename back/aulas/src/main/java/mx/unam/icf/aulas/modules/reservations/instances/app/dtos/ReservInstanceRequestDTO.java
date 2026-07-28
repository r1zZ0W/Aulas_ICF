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

        @NotNull(message = "FIELD_REQUIRED")
        UUID groupUuid,

        @NotNull(message = "FIELD_REQUIRED")
        UUID classroomUuid,

        @NotNull(message = "FIELD_REQUIRED")
        @FutureOrPresent(message = "FIELD_OUT_OF_RANGE")
        LocalDate date,

        @NotEmpty(message = "FIELD_REQUIRED")
        List<Integer> timeSlotIds,

        @NotNull(message = "FIELD_REQUIRED")
        @Positive(message = "FIELD_OUT_OF_RANGE")
        Integer attendeeCount,

        @Size(max = 150, message = "FIELD_OUT_OF_RANGE")
        String title
) {}
