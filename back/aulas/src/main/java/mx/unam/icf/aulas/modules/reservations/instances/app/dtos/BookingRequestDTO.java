package mx.unam.icf.aulas.modules.reservations.instances.app.dtos;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request payload for the atomic booking endpoint ({@code POST /api/v1/reservations/booking}).
 *
 * <p>A single request may create several {@link mx.unam.icf.aulas.modules.reservations.instances.domain.ReservInstance}
 * records — one per target date — all within a single database transaction.
 * The active semester is resolved by the service; the frontend does not send a semester ID.</p>
 *
 * Note: recurrence support was removed — this request represents a single booking
 * for {@code startDate} only. The weekday used is derived from {@code startDate}
 * unless {@code daysOfWeek} is explicitly provided.
 *
 * @param classroomUuid  public UUID of the requested classroom
 * @param attendeeCount  expected number of attendees (must be positive)
 * @param timeSlotIds    ordered list of 30-minute slot IDs to book (1–24, covering 07:00–19:00)
 * @param startDate      reservation date; must not be in the past
 * @param daysOfWeek     optional weekdays hint; when provided the request is validated
 *                       against the weekday(s) but only the single {@code startDate}
 *                       is considered for booking
 * @param title          optional free-text label for this reservation
 *                       (e.g. "Programación I — parcial"); max 150 characters.
 *                       The frontend must {@code .trim()} the value before sending —
 *                       {@code @Size} validates the raw string, so 151 spaces would be
 *                       rejected with 400. The backend normalises blank/empty to {@code null}.
 *
 * @author Ithera
 * @version 1.1
 */
public record BookingRequestDTO(

        @NotNull(message = "Classroom UUID is required")
        UUID classroomUuid,

        @NotNull(message = "Number of attendees is required")
        @Positive(message = "Number of attendees must be positive")
        Integer attendeeCount,

        @NotEmpty(message = "At least one time slot is required")
        List<Integer> timeSlotIds,

        @NotNull(message = "Start date is required")
        @FutureOrPresent(message = "Start date must not be in the past")
        LocalDate startDate,

        /*
         * Weekdays to book. Null or empty → inherit from {@code startDate}'s weekday.
         * When provided, the request is only accepted if {@code startDate}'s weekday
         * is included or when it matches one of the provided days (single-date booking).
         */
        List<DayOfWeek> daysOfWeek,

        @Size(max = 150, message = "Title must not exceed 150 characters")
        String title
) {}
