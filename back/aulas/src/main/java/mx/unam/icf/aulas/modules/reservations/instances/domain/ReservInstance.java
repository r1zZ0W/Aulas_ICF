package mx.unam.icf.aulas.modules.reservations.instances.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
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
 * <p>Each instance corresponds to one day on which the group's pattern fires,
 * carrying its own approval status and the specific classroom assigned for that date.
 * Time-slot bookings are materialized as {@link ReservSlot} records linked to this entity.</p>
 *
 * @author Ithera
 * @version 2.0
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

    /** Current approval status of this instance. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ReservInstanceStatus status;

    /** Purpose or reason for this reservation, as provided by the teacher. */
    @Column(name = "motivo", nullable = false, length = 500)
    private String motivo;

    /** Expected number of attendees for this session. */
    @Column(name = "num_asistentes", nullable = false)
    private Integer numAsistentes;

    /** Individual 30-minute time-slot bookings for this instance. */
    @OneToMany(mappedBy = "instance", fetch = FetchType.LAZY)
    private List<ReservSlot> slots;
}
