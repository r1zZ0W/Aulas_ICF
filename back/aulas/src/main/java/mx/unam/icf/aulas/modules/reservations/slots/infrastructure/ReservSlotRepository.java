package mx.unam.icf.aulas.modules.reservations.slots.infrastructure;

import mx.unam.icf.aulas.modules.reservations.slots.domain.ReservSlot;
import mx.unam.icf.aulas.modules.reservations.slots.domain.ReservSlotId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Bulk-deletes all slots whose denormalized {@code userId} matches the given internal user PK.
     * Used as the first step in the cascade-delete flow when removing a user account.
     *
     * @param userId internal database PK of the user (not the public UUID)
     */
    @Modifying
    @Query("DELETE FROM ReservSlot rs WHERE rs.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
