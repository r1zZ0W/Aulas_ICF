package mx.unam.icf.aulas.modules.reservations.history.app.dtos;

import mx.unam.icf.aulas.modules.reservations.history.domain.ReservationEvent;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Read-only projection of a {@link mx.unam.icf.aulas.modules.reservations.history.domain.ReservationHistory} row.
 *
 * <p>There is intentionally no request DTO for this module — history rows are
 * written exclusively by internal service calls, never by external HTTP clients.</p>
 *
 * @param uuid            public identifier of this history record
 * @param eventType       the type of event that was recorded
 * @param groupUuid       public UUID of the related reservation group (may be null)
 * @param instanceUuid    public UUID of the related reservation instance (may be null)
 * @param performedByUuid public UUID of the user who triggered the event (may be null for system actions)
 * @param performedByName display name of the acting user (may be null for system actions)
 * @param details         optional free-text note providing additional context
 * @param createdAt       timestamp of when the event was recorded
 *
 * @author Ithera
 * @version 1.0
 */
public record ReservationHistoryResponseDTO(
        UUID uuid,
        ReservationEvent eventType,
        UUID groupUuid,
        UUID instanceUuid,
        UUID performedByUuid,
        String performedByName,
        String details,
        LocalDateTime createdAt
) {}
