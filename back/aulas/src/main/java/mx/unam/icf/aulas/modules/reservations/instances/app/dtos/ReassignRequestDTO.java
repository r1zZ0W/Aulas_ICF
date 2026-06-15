package mx.unam.icf.aulas.modules.reservations.instances.app.dtos;

import java.util.List;
import java.util.UUID;

/**
 * Request payload for reassigning an approved reservation to a different classroom
 * and/or a different set of 30-minute time slots (DFR §4.3).
 *
 * <p>Both fields are optional, but at least one must be non-null. Validation is
 * enforced in the service layer via {@link mx.unam.icf.aulas.kernel.domain.exceptions.DomainException}.</p>
 *
 * @param newClassroomUuid public UUID of the destination classroom; {@code null} to keep the current one
 * @param newTimeSlotIds   ordered list of destination slot IDs (1–26, 07:00–20:00); {@code null} to keep current
 *
 * @author Ithera
 * @version 1.0
 */
public record ReassignRequestDTO(
        UUID newClassroomUuid,
        List<Integer> newTimeSlotIds
) {}
