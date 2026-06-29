package mx.unam.icf.aulas.modules.reservations.instances.infrastructure;

import mx.unam.icf.aulas.modules.reservations.instances.domain.ReservInstance;
import mx.unam.icf.aulas.modules.reservations.instances.domain.ReservInstanceStatus;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link ReservInstance} persistence and availability queries.
 *
 * <p>Conflict detection ({@link #existsConflict}, {@link #existsConflictExcluding},
 * {@link #existsUserConflict}, {@link #existsUserConflictExcluding}) operates directly
 * on {@code ReservSlot} rows without a status predicate, because cancelled reservations
 * no longer hold slot rows — the mere presence of a slot means the time is taken.
 * The DB-level backstop is provided by {@code uk_reserv_slots_classroom_time} and
 * {@code uk_reserv_slots_user_time}.</p>
 *
 * @author Ithera
 * @version 3.0
 */
public interface ReservInstanceRepository extends JpaRepository<ReservInstance, Long> {

    /** Finds a reservation instance by its public UUID. */
    Optional<ReservInstance> findByUuid(UUID uuid);

    /**
     * Returns a page of all reservation instances with associations eagerly loaded.
     * Used by the paginated admin listing endpoint.
     */
    @Override
    @EntityGraph(attributePaths = {"group", "group.user", "classroom"})
    Page<ReservInstance> findAll(@NonNull Pageable pageable);

    /**
     * Returns a page of reservation instances belonging to a given user, with associations
     * eagerly loaded. A {@code countQuery} is required because the JOIN traversal would
     * produce incorrect counts without it when Spring Data derives the count query automatically.
     *
     * @param userUuid public UUID of the owning user
     * @param pageable pagination and sort criteria
     */
    @EntityGraph(attributePaths = {"group", "group.user", "classroom"})
    @Query(value = "SELECT ri FROM ReservInstance ri WHERE ri.group.user.uuid = :userUuid",
           countQuery = "SELECT COUNT(ri) FROM ReservInstance ri WHERE ri.group.user.uuid = :userUuid")
    Page<ReservInstance> findByUserUuid(@Param("userUuid") UUID userUuid, Pageable pageable);

    /**
     * Returns active reservation instances for a specific classroom within a date range,
     * with all associations eagerly joined to avoid N+1 queries when the mapper reads
     * {@code classroom.name}, {@code group.user}, and {@code slots.timeSlot}.
     *
     * <p>{@code SELECT DISTINCT} is required because the {@code LEFT JOIN FETCH ri.slots}
     * multiplies rows for instances with several slots; Hibernate deduplicates in memory.</p>
     *
     * @param classroomUuid public UUID of the classroom
     * @param from          start of the date range (inclusive)
     * @param to            end of the date range (inclusive)
     * @param status        status to filter by (pass {@link ReservInstanceStatus#ACTIVA})
     */
    @Query("SELECT DISTINCT ri FROM ReservInstance ri " +
           "JOIN FETCH ri.classroom c " +
           "JOIN FETCH ri.group g " +
           "JOIN FETCH g.user u " +
           "LEFT JOIN FETCH ri.slots s " +
           "LEFT JOIN FETCH s.timeSlot ts " +
           "WHERE c.uuid = :classroomUuid " +
           "AND ri.date BETWEEN :from AND :to " +
           "AND ri.status = :status")
    List<ReservInstance> findActiveByClassroomAndDateRange(
            @Param("classroomUuid") UUID classroomUuid,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("status") ReservInstanceStatus status
    );

    /**
     * Returns active reservation instances across all classrooms within a date range,
     * with all associations eagerly joined to avoid N+1 queries.
     *
     * <p>Used for both the all-rooms availability calendar query (when no
     * {@code classroomUuid} is supplied) and for PDF report generation.</p>
     *
     * @param from   start of the date range (inclusive)
     * @param to     end of the date range (inclusive)
     * @param status status to filter by (pass {@link ReservInstanceStatus#ACTIVA})
     */
    @Query("SELECT DISTINCT ri FROM ReservInstance ri " +
           "JOIN FETCH ri.classroom c " +
           "JOIN FETCH ri.group g " +
           "JOIN FETCH g.user u " +
           "LEFT JOIN FETCH ri.slots s " +
           "LEFT JOIN FETCH s.timeSlot ts " +
           "WHERE ri.date BETWEEN :from AND :to " +
           "AND ri.status = :status")
    List<ReservInstance> findActiveByDateRange(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("status") ReservInstanceStatus status
    );

    /** Returns all reservation instances belonging to a given user (via their groups). */
    @Query("SELECT ri FROM ReservInstance ri WHERE ri.group.user.uuid = :userUuid")
    List<ReservInstance> findByUserUuid(@Param("userUuid") UUID userUuid);

    /**
     * Checks whether any slot conflict exists for a given classroom, date, and set of time slots.
     * No status predicate is applied: cancelled reservations no longer hold slot rows, so the
     * presence of any matching slot row means the classroom is already booked.
     * Used to prevent double-booking before persisting a new reservation instance.
     *
     * @param classroomId  internal PK of the classroom
     * @param date         reservation date
     * @param timeSlotIds  list of time-slot IDs to check
     * @return {@code true} when at least one conflicting slot exists
     */
    @Query("SELECT COUNT(rs) > 0 FROM ReservSlot rs " +
           "WHERE rs.classroomId = :classroomId " +
           "AND rs.date = :date " +
           "AND rs.timeSlot.id IN :timeSlotIds")
    boolean existsConflict(
            @Param("classroomId") Long classroomId,
            @Param("date") LocalDate date,
            @Param("timeSlotIds") List<Integer> timeSlotIds
    );

    /**
     * Like {@link #existsConflict} but excludes a specific instance from the check.
     * Used during reassignment to avoid a false self-conflict when the same
     * classroom/slots are re-requested.
     *
     * @param classroomId  internal PK of the classroom
     * @param date         reservation date
     * @param timeSlotIds  list of time-slot IDs to check
     * @param excludeId    internal PK of the instance to exclude from the check
     * @return {@code true} when at least one conflicting slot exists (excluding self)
     */
    @Query("SELECT COUNT(rs) > 0 FROM ReservSlot rs " +
           "WHERE rs.classroomId = :classroomId " +
           "AND rs.date = :date " +
           "AND rs.timeSlot.id IN :timeSlotIds " +
           "AND rs.instance.id <> :excludeId")
    boolean existsConflictExcluding(
            @Param("classroomId") Long classroomId,
            @Param("date") LocalDate date,
            @Param("timeSlotIds") List<Integer> timeSlotIds,
            @Param("excludeId") Long excludeId
    );

    /**
     * Checks whether a specific user already holds a slot on the given date and
     * time-slot combination (personal schedule conflict detection).
     * Backed by {@code uk_reserv_slots_user_time}.
     *
     * @param userId       internal PK of the user
     * @param date         reservation date
     * @param timeSlotIds  list of time-slot IDs to check
     * @return {@code true} when the user has at least one conflicting slot
     */
    @Query("SELECT COUNT(rs) > 0 FROM ReservSlot rs " +
           "WHERE rs.userId = :userId " +
           "AND rs.date = :date " +
           "AND rs.timeSlot.id IN :timeSlotIds")
    boolean existsUserConflict(
            @Param("userId") Long userId,
            @Param("date") LocalDate date,
            @Param("timeSlotIds") List<Integer> timeSlotIds
    );

    /**
     * Like {@link #existsUserConflict} but excludes a specific instance from the check.
     * Used during reassignment to avoid a false self-conflict for the owning teacher.
     *
     * @param userId       internal PK of the user
     * @param date         reservation date
     * @param timeSlotIds  list of time-slot IDs to check
     * @param excludeId    internal PK of the instance to exclude from the check
     * @return {@code true} when the user has at least one conflicting slot (excluding self)
     */
    @Query("SELECT COUNT(rs) > 0 FROM ReservSlot rs " +
           "WHERE rs.userId = :userId " +
           "AND rs.date = :date " +
           "AND rs.timeSlot.id IN :timeSlotIds " +
           "AND rs.instance.id <> :excludeId")
    boolean existsUserConflictExcluding(
            @Param("userId") Long userId,
            @Param("date") LocalDate date,
            @Param("timeSlotIds") List<Integer> timeSlotIds,
            @Param("excludeId") Long excludeId
    );

    /**
     * Bulk-deletes all reservation instances whose owning group belongs to the given user.
     * Must be called <em>after</em> all {@code ReservSlot} rows have been removed (child before
     * parent), and <em>before</em> the groups themselves are deleted.
     *
     * @param userId internal database PK of the user (not the public UUID)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ReservInstance ri WHERE ri.group.id IN " +
           "(SELECT g.id FROM ReservationGroup g WHERE g.user.id = :userId)")
    void deleteAllByOwnerId(@Param("userId") Long userId);

    /**
     * Returns the distinct internal group IDs of all reservation instances that belong to the
     * given classroom. Called <em>before</em> deleting the instances so we can identify which
     * {@link mx.unam.icf.aulas.modules.reservations.groups.domain.ReservationGroup}s may become
     * orphans (zero remaining instances) after the cascade.
     *
     * @param classroomId internal database PK of the classroom (not the public UUID)
     * @return list of distinct group IDs
     */
    @Query("SELECT DISTINCT ri.group.id FROM ReservInstance ri WHERE ri.classroom.id = :classroomId")
    List<Long> findGroupIdsByClassroomId(@Param("classroomId") Long classroomId);

    /**
     * Bulk-deletes all reservation instances assigned to the given classroom.
     * Must be called <em>after</em> all {@code ReservSlot} rows for that classroom have been
     * removed, and <em>before</em> the classroom row itself is deleted.
     *
     * @param classroomId internal database PK of the classroom (not the public UUID)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ReservInstance ri WHERE ri.classroom.id = :classroomId")
    void deleteAllByClassroomId(@Param("classroomId") Long classroomId);

    /**
     * Returns whether any reservation instance still belongs to the given group.
     * Used after a classroom cascade-delete to detect orphan groups (groups whose
     * every instance was in the deleted classroom).
     *
     * @param groupId internal database PK of the group
     * @return {@code true} if at least one instance remains in the group
     */
    boolean existsByGroup_Id(Long groupId);
}
