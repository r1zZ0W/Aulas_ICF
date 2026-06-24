package mx.unam.icf.aulas.modules.reservations.instances.app.dtos;

import mx.unam.icf.aulas.modules.academic.timeslots.app.dtos.TimeSlotDTO;
import mx.unam.icf.aulas.modules.reservations.instances.domain.ReservInstanceStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response payload exposing a {@link mx.unam.icf.aulas.modules.reservations.instances.domain.ReservInstance} through the API.
 *
 * <p>Both the group and classroom are identified by their public UUIDs.
 * Audit timestamps are included for transparency in the admin interface.
 * The {@code timeSlots} list is always ordered by {@code startTime ASC} so
 * the frontend can derive event start/end times without sorting.
 * User information (UUID, full name, and username) is included to identify
 * who made the reservation.</p>
 *
 * @param uuid           public UUID of this reservation instance
 * @param groupUuid      public UUID of the parent reservation group
 * @param userUuid       public UUID of the user who made the reservation
 * @param userFullName   full name (firstName + lastNames) of the user
 * @param userUsername   username of the user
 * @param classroomUuid  public UUID of the assigned classroom
 * @param classroomName  display name of the assigned classroom (calendar labels)
 * @param date           date of this occurrence
 * @param status         current lifecycle status
 * @param attendeeCount  expected number of attendees
 * @param timeSlots      ordered list of 30-minute blocks reserved for this instance
 * @param createdAt      timestamp when this record was created
 *
 * @author Ithera
 * @version 4.0
 */
public record ReservInstanceResponseDTO(
        UUID uuid,
        UUID groupUuid,
        UUID userUuid,
        String userFullName,
        String userUsername,
        UUID classroomUuid,
        String classroomName,
        LocalDate date,
        ReservInstanceStatus status,
        Integer attendeeCount,
        List<TimeSlotDTO> timeSlots,
        LocalDateTime createdAt
) {}
