package mx.unam.icf.aulas.modules.reservations.instances.app.exceptions;

import java.time.LocalDate;

/**
 * Thrown when a bulk booking request ({@code POST /api/v1/reservations/booking})
 * detects a time-slot conflict via the pre-flight batch query.
 *
 * <p>Carries the first conflicting {@code date} and {@code timeSlotId} so the
 * global exception handler can surface them as structured JSON (HTTP 409), enabling
 * the frontend to show a human-readable, specific error message.</p>
 *
 * @author Ithera
 * @version 1.0
 */
public class ReservationConflictException extends RuntimeException {

    private final LocalDate conflictDate;
    private final Integer   conflictTimeSlotId;

    /**
     * @param date       the first date on which the conflict was detected
     * @param timeSlotId the 30-minute slot ID (1–24) that is already taken
     */
    public ReservationConflictException(LocalDate date, Integer timeSlotId) {
        super("Slot conflict detected on " + date + " for time slot " + timeSlotId);
        this.conflictDate       = date;
        this.conflictTimeSlotId = timeSlotId;
    }

    /** Returns the conflicting date. */
    public LocalDate getConflictDate() { return conflictDate; }

    /** Returns the conflicting time-slot ID (1–24). */
    public Integer getConflictTimeSlotId() { return conflictTimeSlotId; }
}
