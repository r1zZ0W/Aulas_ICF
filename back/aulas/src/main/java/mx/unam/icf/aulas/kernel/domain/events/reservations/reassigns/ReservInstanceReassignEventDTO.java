package mx.unam.icf.aulas.kernel.domain.events.reservations.reassigns;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Event payload published when a reservation instance is reassigned from one
 * classroom to another (and/or to a different time-slot block).
 *
 * <p>All fields are primitive values or value types resolved <em>before</em> the event
 * is published, so listeners can safely process this payload in a separate thread
 * without risk of lazy-loading or detached-entity exceptions.</p>
 *
 * @param teacherEmail     institutional email of the teacher who owns the reservation
 * @param teacherFullName  display name of the teacher
 * @param date             date of the reassigned reservation
 * @param oldClassroomName name of the classroom before the reassignment
 * @param newClassroomName name of the classroom after the reassignment
 * @param startTime        start time of the earliest booked slot after reassignment
 * @param endTime          end time of the latest booked slot after reassignment
 * @param reservationId    public UUID of the reassigned reservation instance
 * @param adminEmails      email addresses of all active ADMIN users to notify
 */
public record ReservInstanceReassignEventDTO(
        String teacherEmail,
        String teacherFullName,
        LocalDate date,
        String oldClassroomName,
        String newClassroomName,
        LocalTime startTime,
        LocalTime endTime,
        UUID reservationId,
        List<String> adminEmails
) {}
