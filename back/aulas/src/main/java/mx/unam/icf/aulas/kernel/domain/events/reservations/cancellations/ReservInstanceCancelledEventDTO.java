package mx.unam.icf.aulas.kernel.domain.events.reservations.cancellations;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Event payload published when a reservation instance is cancelled
 * (either by the owning teacher or by an administrator).
 *
 * <p>All fields are primitive values or value types resolved <em>before</em> the event
 * is published (importantly, <em>before</em> the associated {@code ReservSlot} rows are
 * deleted), so listeners can safely process this payload in a separate thread without
 * risk of lazy-loading or detached-entity exceptions.</p>
 *
 * @param maestroEmail    institutional email of the teacher who owns the reservation
 * @param maestroFullName display name of the teacher
 * @param classroomName   name of the classroom that was freed
 * @param date            date of the cancelled reservation
 * @param startTime       start time of the earliest slot (may be {@code null} if no slots existed)
 * @param endTime         end time of the latest slot (may be {@code null} if no slots existed)
 * @param reservationId   public UUID of the cancelled reservation instance
 * @param cancelledByAdmin {@code true} when an administrator performed the cancellation;
 *                         {@code false} when the teacher cancelled their own reservation
 * @param reason          optional cancellation reason supplied by the actor (may be {@code null})
 * @param adminEmails     email addresses of all active ADMIN users to notify
 */
public record ReservInstanceCancelledEventDTO(
        String maestroEmail,
        String maestroFullName,
        String classroomName,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        UUID reservationId,
        boolean cancelledByAdmin,
        String reason,
        List<String> adminEmails
) {}
