package mx.unam.icf.aulas.modules.reservations.instances.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mx.unam.icf.aulas.kernel.domain.entities.BaseEntity;
import mx.unam.icf.aulas.kernel.infrastructure.persistance.UuidBinaryConverter;
import mx.unam.icf.aulas.modules.reservations.groups.domain.ReservationGroup;
import mx.unam.icf.aulas.modules.reservations.slots.domain.ReservSlot;
import mx.unam.icf.aulas.modules.resources.classrooms.domain.Classroom;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Entity representing a single date occurrence of a recurring {@link ReservationGroup}.
 *
 * <p>Each instance corresponds to one day on which the group's pattern fires. It is
 * active ({@link ReservInstanceStatus#ACTIVE}) from the moment of creation and
 * occupies the assigned classroom immediately. Time-slot bookings are materialized as
 * {@link ReservSlot} records linked to this entity.</p>
 *
 * @author Ithera
 * @version 3.0
 * @see ReservationGroup
 * @see ReservSlot
 */
@Entity
@Table(name = "reserv_instances", indexes = {
        @Index(name = "idx_reserv_instances_group_status", columnList = "group_id, status"),
        // Covers ReportStatisticsRepository.countInstancesPerGroup's `status + date` range scan
        // followed by GROUP BY group_id (recurrence donut/tasa on Reportes y Estadísticas).
        // See docs/migration_v1.4__reports_status_date_group_index.sql for the manual DDL.
        @Index(name = "idx_reserv_instances_status_date_group", columnList = "status, date, group_id")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class ReservInstance extends BaseEntity {

    /** Public UUID exposed through external APIs, stored as BINARY(16). */
    @Convert(converter = UuidBinaryConverter.class)
    @Column(name = "uuid", nullable = false, unique = true, columnDefinition = "BINARY(16)")
    private UUID uuid = UUID.randomUUID();

    /** Recurring group this instance belongs to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private ReservationGroup group;

    /** Classroom assigned for this specific date occurrence. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "classroom_id", nullable = false)
    private Classroom classroom;

    /** Date of this occurrence. */
    @Column(name = "date", nullable = false)
    private LocalDate date;

    /** Current lifecycle status of this instance; defaults to {@link ReservInstanceStatus#ACTIVE} on creation. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ReservInstanceStatus status = ReservInstanceStatus.ACTIVE;

    /** Expected number of attendees for this session. */
    @Column(name = "attendee_count", nullable = false)
    private Integer attendeeCount;

    /**
     * Whether this instance has been reassigned to a different classroom and/or
     * time-slot block by an administrator.
     *
     * <p>Defaults to {@code false} on creation and set permanently to {@code true}
     * by {@link mx.unam.icf.aulas.modules.reservations.instances.app.ReservInstanceService#reassign}
     * so the frontend can display a contextual "Reasignada" badge without an N+1
     * join against the audit log. The instance status remains {@link ReservInstanceStatus#ACTIVE}
     * after reassignment; this flag is a display hint only.</p>
     */
    @Column(name = "reassigned", nullable = false)
    private Boolean reassigned = false;

    /**
     * Optional free-text label for this reservation (e.g. "Programación I — parcial").
     *
     * <p>Nullable; when absent the frontend falls back to {@code classroomName} as the
     * display text. Always stored as {@code NULL} (never as an empty string) — the
     * {@link #setTitle(String)} setter normalizes blank input to {@code null}.</p>
     */
    @Column(name = "title", length = 150)
    private String title;

    /**
     * Sets the title, normalizing blank or whitespace-only values to {@code null}.
     *
     * <p>Centralizing this invariant in the setter guarantees consistent state
     * regardless of the caller (service, MapStruct, or tests), without relying on
     * {@code @PrePersist}/{@code @PreUpdate} lifecycle callbacks whose execution
     * timing under Hibernate's flush-mode can be unreliable.</p>
     *
     * @param title the raw value from the request; {@code null} and blank strings both
     *              result in {@code null} being stored
     */
    public void setTitle(String title) {
        this.title = (title == null || title.isBlank()) ? null : title.trim();
    }

    /** Individual 30-minute time-slot bookings for this instance, ordered by start time. */
    @OneToMany(mappedBy = "instance", fetch = FetchType.LAZY)
    @OrderBy(value = "timeSlot.id ASC")
    @BatchSize(size = 50)
    private List<ReservSlot> slots;
}
