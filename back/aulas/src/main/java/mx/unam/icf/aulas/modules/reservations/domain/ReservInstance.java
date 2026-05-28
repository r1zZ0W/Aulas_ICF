package mx.unam.icf.aulas.modules.reservations.domain;

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
import mx.unam.icf.aulas.modules.resources.classrooms.domain.Classroom;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Entity representing a single date-specific occurrence of a {@link ReservationGroup}.
 *
 * Each instance targets one classroom on one date and holds the concrete time slots
 * booked via {@link ReservSlot} records.
 *
 * @author Ithera
 * @version 1.0
 * @see ReservationGroup
 * @see ReservSlot
 * @see ReservInstanceStatus
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

    /** The parent reservation group this instance belongs to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private ReservationGroup group;

    /** The classroom reserved for this specific date. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "classroom_id", nullable = false)
    private Classroom classroom;

    /** The specific date of this reservation occurrence. */
    @Column(name = "date", nullable = false)
    private LocalDate date;

    /** Lifecycle status of this individual occurrence. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ReservInstanceStatus status;

    /** Time slot bookings belonging to this instance. */
    @OneToMany(mappedBy = "instance", fetch = FetchType.LAZY)
    private List<ReservSlot> slots;
}
