package mx.unam.icf.aulas.modules.reservations.instances.infrastructure;

import mx.unam.icf.aulas.modules.reservations.instances.domain.ReservInstance;
import mx.unam.icf.aulas.modules.reservations.instances.domain.ReservInstanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link ReservInstance} persistence and availability queries.
 *
 * @author Ithera
 * @version 2.0
 */
public interface ReservInstanceRepository extends JpaRepository<ReservInstance, Long> {

    /** Finds a reservation instance by its public UUID. */
    Optional<ReservInstance> findByUuid(UUID uuid);

    /** Returns all reservation instances with the given status. */
    List<ReservInstance> findByStatus(ReservInstanceStatus status);

    /**
     * Returns approved reservation instances for a specific classroom within a date range.
     * Used for availability calendar queries.
     */
    @Query("SELECT ri FROM ReservInstance ri WHERE ri.classroom.uuid = :classroomUuid AND ri.date BETWEEN :from AND :to AND ri.status = :status")
    List<ReservInstance> findApprovedByClassroomAndDateRange(
            @Param("classroomUuid") UUID classroomUuid,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("status") ReservInstanceStatus status
    );

    /**
     * Returns approved reservation instances across all classrooms within a date range.
     * Used for PDF report generation.
     */
    @Query("SELECT ri FROM ReservInstance ri WHERE ri.date BETWEEN :from AND :to AND ri.status = :status")
    List<ReservInstance> findApprovedByDateRange(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("status") ReservInstanceStatus status
    );

    /** Returns all reservation instances belonging to a given user (via their groups). */
    @Query("SELECT ri FROM ReservInstance ri WHERE ri.group.user.uuid = :userUuid")
    List<ReservInstance> findByUserUuid(@Param("userUuid") UUID userUuid);

    /**
     * Checks whether any approved slot conflicts exist for a given classroom, date, and set of time slots.
     * Used to prevent double-booking before persisting a new reservation instance.
     */
    @Query("SELECT COUNT(rs) > 0 FROM ReservSlot rs WHERE rs.classroomId = :classroomId AND rs.date = :date AND rs.timeSlot.id IN :timeSlotIds AND rs.instance.status = :status")
    boolean existsConflict(
            @Param("classroomId") Long classroomId,
            @Param("date") LocalDate date,
            @Param("timeSlotIds") List<Integer> timeSlotIds,
            @Param("status") ReservInstanceStatus status
    );
}
