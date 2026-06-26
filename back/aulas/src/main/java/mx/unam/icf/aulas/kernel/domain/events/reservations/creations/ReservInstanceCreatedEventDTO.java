package mx.unam.icf.aulas.kernel.domain.events.reservations.creations;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Event payload published when one or more reservation instances are successfully created.
 *
 * <p>All fields are primitive values or value types resolved <em>before</em> the event
 * is published, so listeners can safely process this payload in a separate thread
 * without risk of lazy-loading or detached-entity exceptions.</p>
 *
 * @param maestroEmail    institutional email of the teacher who made the reservation
 * @param maestroFullName display name of the teacher
 * @param classroomName   name of the reserved classroom
 * @param date            date of the first (or only) reservation instance
 * @param startTime       start time of the earliest booked slot
 * @param endTime         end time of the latest booked slot
 * @param reservationId   public UUID of the reservation instance (or group for bulk bookings)
 * @param adminEmails     email addresses of all active ADMIN users to notify
 */
public record ReservInstanceCreatedEventDTO(
        String maestroEmail,
        String maestroFullName,
        String classroomName,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        UUID reservationId,
        List<String> adminEmails
) {}
