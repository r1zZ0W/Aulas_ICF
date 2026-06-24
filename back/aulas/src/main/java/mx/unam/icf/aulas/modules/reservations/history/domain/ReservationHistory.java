package mx.unam.icf.aulas.modules.reservations.history.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mx.unam.icf.aulas.kernel.domain.entities.BaseEntity;
import mx.unam.icf.aulas.kernel.infrastructure.persistance.UuidBinaryConverter;
import mx.unam.icf.aulas.modules.access.users.domain.User;
import mx.unam.icf.aulas.modules.reservations.groups.domain.ReservationGroup;
import mx.unam.icf.aulas.modules.reservations.instances.domain.ReservInstance;

import java.util.UUID;

/**
 * Persistent record of a single auditable event on a reservation.
 *
 * <p>Each row captures <em>what happened</em> ({@link ReservationEvent eventType}),
 * <em>when</em> (inherited {@code createdAt} set by {@link BaseEntity#onCreate()}),
 * <em>on which reservation</em> (nullable FK to {@link ReservInstance} and/or
 * {@link ReservationGroup}), <em>who acted</em> (nullable FK to {@link User}), and
 * an optional free-text note for additional context.</p>
 *
 * <h3>Design decisions</h3>
 * <ul>
 *   <li>Both {@code group} and {@code instance} FKs are nullable so that events
 *       can be logged at either granularity without artificial constraints.</li>
 *   <li>{@code performedBy} is nullable to accommodate system-initiated or
 *       background actions where no authenticated HTTP principal is present
 *       (see {@code ReservationHistoryService.resolveActor()}).</li>
 *   <li>This entity is a <strong>Change History</strong>, not a Security Audit.
 *       Rows are written inside the caller's transaction; if that transaction rolls
 *       back the history row disappears too — only <em>successful</em> changes are
 *       recorded. Attempted/failed-action telemetry belongs to application logs.</li>
 *   <li>The column name is {@code event_type} rather than {@code event} to avoid
 *       collision with the reserved keyword {@code EVENT} in MySQL/MariaDB.</li>
 * </ul>
 *
 * @author Ithera
 * @version 1.0
 * @see ReservationEvent
 */
@Entity
@Table(name = "reservation_history")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class ReservationHistory extends BaseEntity {

    /** Public UUID exposed through external APIs, stored as BINARY(16). */
    @Convert(converter = UuidBinaryConverter.class)
    @Column(name = "uuid", nullable = false, unique = true, columnDefinition = "BINARY(16)")
    private UUID uuid = UUID.randomUUID();

    /**
     * The recurring reservation group this event belongs to.
     * Nullable — some events may only reference a specific instance.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private ReservationGroup group;

    /**
     * The specific date occurrence this event belongs to.
     * Nullable — group-level events may not target a single instance.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instance_id")
    private ReservInstance instance;

    /**
     * The user who triggered this event.
     * Nullable — background / system-initiated actions may have no HTTP principal.
     * When null, the {@code details} field should describe the acting agent.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by_user_id")
    private User performedBy;

    /**
     * Categorical classification of the event.
     * Stored as its string name to remain human-readable and resilient to reordering.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private ReservationEvent eventType;

    /**
     * Optional free-text note providing human-readable context.
     * Required when {@code performedBy} is null (background/system action).
     */
    @Column(name = "details", length = 500)
    private String details;
}
