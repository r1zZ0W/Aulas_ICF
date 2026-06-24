package mx.unam.icf.aulas.modules.reservations.instances.app.dtos;

import java.time.LocalDate;

/**
 * Structured payload returned as the {@code data} field of a 409 Conflict response
 * when a bulk booking request collides with an existing slot.
 *
 * <p>Carrying the first conflicting {@code date} and {@code timeSlotId} lets the
 * frontend display a specific message such as "El martes 23 de junio a las 10:00
 * ya está ocupado" instead of a generic conflict notice.</p>
 *
 * @param date        the first date on which the conflict was detected
 * @param timeSlotId  the 30-minute slot ID (1–24) that is already taken
 *
 * @author Ithera
 * @version 1.0
 */
public record ConflictDetailDTO(LocalDate date, Integer timeSlotId) {}
