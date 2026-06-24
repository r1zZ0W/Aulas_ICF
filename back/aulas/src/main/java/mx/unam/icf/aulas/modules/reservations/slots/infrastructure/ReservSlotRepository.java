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
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ReservSlot rs WHERE rs.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);

    /**
     * Bulk-deletes all slots whose denormalized {@code classroomId} matches the given internal
     * classroom PK. Must run <em>before</em> deleting the reservation instances that own those
     * slots (child before parent), and before deleting the classroom itself.
     *
     * <p>{@code clearAutomatically} + {@code flushAutomatically} are required so that the
     * JPQL bulk DELETE is flushed and the L1 cache is cleared before subsequent operations
     * in the same transaction see a consistent state.</p>
     *
     * @param classroomId internal database PK of the classroom (not the public UUID)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ReservSlot rs WHERE rs.classroomId = :classroomId")
    void deleteAllByClassroomId(@Param("classroomId") Long classroomId);

    // ── Bulk conflict detection for atomic booking ────────────────────────────

    /**
     * Returns all slot rows where the given classroom already has bookings on any of the
     * supplied dates at any of the supplied time slots, ordered chronologically so that the
     * first element is the earliest conflict.
     *
     * <p>Used by the atomic booking endpoint to run a single pre-flight query across all
     * target dates instead of issuing one {@code existsConflict} call per date (O(N) → O(1)).</p>
     *
     * @param classroomId internal PK of the classroom
     * @param slotIds     time-slot IDs to check (e.g. [3, 4, 5] for a 90-minute block)
     * @param dates       all target dates for the booking (e.g. every Tuesday in the semester)
     * @return conflicting slots ordered by date ASC, timeSlot.id ASC; empty if no conflict
     */
    @Query("SELECT rs FROM ReservSlot rs " +
           "WHERE rs.classroomId = :classroomId " +
           "AND rs.timeSlot.id IN :slotIds " +
           "AND rs.date IN :dates " +
           "ORDER BY rs.date ASC, rs.timeSlot.id ASC")
    List<ReservSlot> findClassroomConflicts(
            @Param("classroomId") Long classroomId,
            @Param("slotIds")     List<Integer> slotIds,
            @Param("dates")       List<java.time.LocalDate> dates
    );

    /**
     * Returns all slot rows where the given user already has bookings on any of the
     * supplied dates at any of the supplied time slots, ordered chronologically so that the
     * first element is the earliest self-conflict.
     *
     * <p>Personal schedule conflict detection for the atomic booking endpoint.
     * Backed by {@code uk_reserv_slots_user_time}.</p>
     *
     * @param userId  internal PK of the user
     * @param slotIds time-slot IDs to check
     * @param dates   all target dates for the booking
     * @return conflicting slots ordered by date ASC, timeSlot.id ASC; empty if no conflict
     */
    @Query("SELECT rs FROM ReservSlot rs " +
           "WHERE rs.userId = :userId " +
           "AND rs.timeSlot.id IN :slotIds " +
           "AND rs.date IN :dates " +
           "ORDER BY rs.date ASC, rs.timeSlot.id ASC")
    List<ReservSlot> findUserConflicts(
            @Param("userId")  Long userId,
            @Param("slotIds") List<Integer> slotIds,
            @Param("dates")   List<java.time.LocalDate> dates
    );
}
