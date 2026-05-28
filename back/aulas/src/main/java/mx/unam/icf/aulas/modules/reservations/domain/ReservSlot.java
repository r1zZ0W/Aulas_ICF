package mx.unam.icf.aulas.modules.reservations.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mx.unam.icf.aulas.modules.academic.timeslots.domain.TimeSlot;

import java.time.LocalDate;

/**
 * Entity representing a single booked 30-minute time slot within a {@link ReservInstance}.
 *
 * Designed as an independent entity to support real-time availability queries.
 * The {@code classroomId} and {@code userId} fields are stored as plain numeric values
 * (not full entity references) to enable direct indexed overlap detection via the database's
 * unique indexes ({@code idx_unique_classroom_time}, {@code idx_unique_user_time}) without
 * triggering unnecessary lazy-load chains.
 *
 * @author Ithera
 * @version 1.0
 * @see ReservInstance
 * @see TimeSlot
 */
@Entity
@Table(name = "reserv_slots")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class ReservSlot {

    /** Composite primary key composed of instanceId and timeSlotId. */
    @EmbeddedId
    private ReservSlotId id;

    /** The reservation instance this slot belongs to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("instanceId")
    @JoinColumn(name = "instance_id")
    private ReservInstance instance;

    /** The specific 30-minute time slot being booked. */
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("timeSlotId")
    @JoinColumn(name = "time_slot_id")
    private TimeSlot timeSlot;

    /**
     * Denormalized classroom identifier for fast overlap detection.
     * Corresponds to the {@code UNIQUE INDEX idx_unique_classroom_time (classroom_id, date, time_slot_id)}.
     */
    @Column(name = "classroom_id", nullable = false)
    private Long classroomId;

    /**
     * Denormalized user identifier for personal schedule conflict detection.
     * Corresponds to the {@code UNIQUE INDEX idx_unique_user_time (user_id, date, time_slot_id)}.
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Date of the booked slot, duplicated for efficient indexed range queries. */
    @Column(name = "date", nullable = false)
    private LocalDate date;
}
