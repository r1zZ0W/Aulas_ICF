package mx.unam.icf.aulas.modules.reservations.slots.infrastructure;

import mx.unam.icf.aulas.modules.reservations.slots.domain.ReservSlot;
import mx.unam.icf.aulas.modules.reservations.slots.domain.ReservSlotId;
import org.springframework.data.jpa.repository.JpaRepository;

import mx.unam.icf.aulas.modules.reservations.instances.domain.ReservInstance;

import java.util.List;

/**
 * Spring Data repository for {@link ReservSlot} persistence operations.
 *
 * @author Ithera
 * @version 2.0
 */
public interface ReservSlotRepository extends JpaRepository<ReservSlot, ReservSlotId> {

    /** Returns all slots belonging to the given reservation instance. */
    List<ReservSlot> findByInstance(ReservInstance instance);

    /**
     * Deletes all slots belonging to the given reservation instance.
     * Used during reassignment when the time-slot set changes (delete + recreate pattern).
     */
    void deleteByInstance(ReservInstance instance);
}
