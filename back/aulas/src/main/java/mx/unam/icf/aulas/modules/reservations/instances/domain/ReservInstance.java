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
@Table(name = "reserv_instances")
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

    /** Individual 30-minute time-slot bookings for this instance, ordered by start time. */
    @OneToMany(mappedBy = "instance", fetch = FetchType.LAZY)
    @OrderBy(value = "timeSlot.id ASC")
    private List<ReservSlot> slots;
}
