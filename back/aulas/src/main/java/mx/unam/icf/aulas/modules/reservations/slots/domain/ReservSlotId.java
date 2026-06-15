package mx.unam.icf.aulas.modules.reservations.slots.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

/**
 * Composite primary key for the {@link ReservSlot} entity.
 *
 * <p>Combines the owning reservation instance and the specific 30-minute time slot.
 * Implements {@link Serializable} as required by the JPA specification.</p>
 *
 * @author Ithera
 * @version 1.0
 */
@Embeddable
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode
public class ReservSlotId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** FK column referencing {@code reserv_instances.id}. */
    @Column(name = "instance_id")
    private Long instanceId;

    /** FK column referencing {@code time_slots.id}. */
    @Column(name = "time_slot_id")
    private Integer timeSlotId;
}
