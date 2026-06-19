package mx.unam.icf.aulas.modules.resources.classrooms.infrastructure;

import mx.unam.icf.aulas.modules.resources.classrooms.app.dtos.ClassroomStatsDTO;
import mx.unam.icf.aulas.modules.resources.classrooms.domain.Classroom;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Classroom entity data access operations.
 *
 * This interface extends {@link JpaRepository} to provide standard CRUD (Create, Read, Update, Delete)
 * operations for classroom entities. It leverages Spring Data JPA to automatically generate the
 * implementation at runtime, enabling object-relational mapping to the database.
 *
 * The repository manages persistence for {@link Classroom} entities with Long as the identifier type.
 *
 * @author Ithera
 * @version 1.0
 * @see Classroom
 */
@Repository
public interface ClassroomRepository extends JpaRepository<Classroom, Long> {

    /**
     * Finds a classroom by its public UUID.
     *
     * @param uuid the public UUID to search by
     * @return an optional classroom when found
     */
    Optional<Classroom> findByUuid(UUID uuid);

    /** Finds a classroom by its unique name, used to enforce name uniqueness before saving. */
    Optional<Classroom> findByName(String name);

    /** Returns all classrooms that are currently active (isActive = true). */
    List<Classroom> findByIsActiveTrue();

    /** Returns a page of active classrooms, used by the paginated Maestro endpoint. */
    Page<Classroom> findByIsActiveTrue(Pageable pageable);

    /**
     * Full-text search across all classrooms (active and inactive). Used by the ADMIN listing.
     *
     * <p>Performs a case-insensitive {@code LIKE} match on {@code name} and {@code description}.</p>
     *
     * @param q        search term (already trimmed by the caller; never {@code null})
     * @param pageable pagination and sort criteria
     * @return a page of matching classrooms
     */
    @Query("""
            SELECT c FROM Classroom c
            WHERE (LOWER(c.name)        LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(c.description) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Classroom> search(@Param("q") String q, Pageable pageable);

    /**
     * Full-text search restricted to active classrooms. Used by the Maestro listing.
     *
     * <p>Performs a case-insensitive {@code LIKE} match on {@code name} and {@code description},
     * limited to classrooms where {@code isActive = true}.</p>
     *
     * @param q        search term (already trimmed by the caller; never {@code null})
     * @param pageable pagination and sort criteria
     * @return a page of matching active classrooms
     */
    @Query("""
            SELECT c FROM Classroom c
            WHERE c.isActive = true
              AND (LOWER(c.name)        LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(c.description) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Classroom> searchActive(@Param("q") String q, Pageable pageable);

    /**
     * Bulk-clears the {@code linked_room_id} FK for all classrooms that currently
     * point to the given parent. Called as part of the deactivation flow (orphan
     * effect, option A) to prevent child classrooms from referencing an inactive parent.
     *
     * <p>Must run inside an active transaction (the caller is {@code @Transactional}).</p>
     *
     * @param parentId internal database PK of the parent classroom (not the public UUID)
     */
    @Modifying
    @Query("UPDATE Classroom c SET c.linkedRoom = null WHERE c.linkedRoom.id = :parentId")
    void unlinkChildren(@Param("parentId") Long parentId);

    /**
     * Returns a single-row aggregate with counts for the admin stats dashboard.
     *
     * <p>Resolves all three counters in one database round-trip using a JPQL
     * constructor expression, without materializing the full classroom list.
     * {@code COALESCE} guards against {@code NULL} when the table is empty,
     * ensuring the primitive {@code long} fields in {@link ClassroomStatsDTO}
     * are never unboxed from {@code null}.</p>
     *
     * @return a {@link ClassroomStatsDTO} with total / available / notAvailable counts
     */
    @Query("""
            SELECT new mx.unam.icf.aulas.modules.resources.classrooms.app.dtos.ClassroomStatsDTO(
                COUNT(c),
                COALESCE(SUM(CASE WHEN c.isActive = true  THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN c.isActive = false OR c.isActive IS NULL THEN 1L ELSE 0L END), 0L))
            FROM Classroom c
            """)
    ClassroomStatsDTO fetchStats();
}
