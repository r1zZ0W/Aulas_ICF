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
 * <p>Recurrence logic:</p>
 * <ul>
 *   <li>If {@code repeatUntil} is {@code null}, a single reservation is created on {@code startDate}.</li>
 *   <li>If {@code repeatUntil} is set, reservations are created for every matching weekday
 *       between {@code startDate} and {@code repeatUntil} (inclusive).</li>
 *   <li>{@code daysOfWeek} controls which weekdays are booked. When {@code null}/empty,
 *       the weekday of {@code startDate} is used (e.g., clicking Tuesday → only Tuesdays).
 *       When populated, all listed days are booked (e.g., [TUESDAY, THURSDAY] for a
 *       twice-weekly class).</li>
 * </ul>
 *
 * @param classroomUuid  public UUID of the requested classroom
 * @param attendeeCount  expected number of attendees (must be positive)
 * @param timeSlotIds    ordered list of 30-minute slot IDs to book (1–24, covering 07:00–19:00)
 * @param startDate      first (or only) reservation date; must not be in the past
 * @param repeatUntil    last date of the recurrence (inclusive); {@code null} = single occurrence.
 *                       Must not exceed the active semester's end date.
 * @param daysOfWeek     weekdays to repeat on; {@code null}/empty = derive from {@code startDate}
 *
 * @author Ithera
 * @version 1.0
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

        /** Null = single occurrence; non-null = weekly recurrence until this date (inclusive). */
        LocalDate repeatUntil,

        /**
         * Weekdays to book. Null or empty → inherit from {@code startDate}'s weekday.
         * Populated → use all listed days within the recurrence window.
         */
        List<DayOfWeek> daysOfWeek
) {}
