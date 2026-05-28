package mx.unam.icf.aulas.modules.academic.timeslots.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/**
 * Entity class representing a 30-minute time slot within the academic day.
 *
 * Slot IDs are pre-seeded (1–24, covering 07:00–19:00) and never auto-generated,
 * so no {@code @GeneratedValue} is declared. This entity does not extend
 * {@code BaseEntity} as it carries no UUID or audit timestamps.
 *
 * @author Ithera
 * @version 1.0
 */
@Entity
@Table(name = "time_slots")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class TimeSlot {

    /** Pre-seeded numeric identifier (1–24). */
    @Id
    private Integer id;

    /** Start time of this slot (inclusive). */
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    /** End time of this slot (exclusive, start_time + 30 min). */
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;
}
