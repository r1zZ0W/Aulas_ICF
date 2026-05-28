package mx.unam.icf.aulas.modules.academic.timeslots.app.dtos;

import java.time.LocalTime;

/**
 * Payload for the {@code TimeSlot} catalog entity.
 *
 * <p>Used for both request and response payloads. Time slots are pre-seeded (1–24);
 * the {@code id} field is required on requests and must reference an existing slot.</p>
 *
 * @param id        slot identifier (1–24)
 * @param startTime start time of the slot (inclusive)
 * @param endTime   end time of the slot (exclusive; start + 30 min)
 *
 * @author Ithera
 * @version 2.0
 */
public record TimeSlotDTO(
        Integer id,
        LocalTime startTime,
        LocalTime endTime
) {}
