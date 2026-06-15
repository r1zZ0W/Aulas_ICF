package mx.unam.icf.aulas.modules.reservations.slots.infrastructure;

import mx.unam.icf.aulas.modules.reservations.slots.domain.ReservSlot;
import mx.unam.icf.aulas.modules.reservations.slots.domain.ReservSlotId;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link ReservSlot} persistence operations.
 *
 * @author Ithera
 * @version 1.0
 */
public interface ReservSlotRepository extends JpaRepository<ReservSlot, ReservSlotId> {}
