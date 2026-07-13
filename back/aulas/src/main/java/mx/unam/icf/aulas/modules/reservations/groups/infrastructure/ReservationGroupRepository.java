package mx.unam.icf.aulas.modules.reservations.groups.infrastructure;

import mx.unam.icf.aulas.modules.reservations.groups.domain.ReservationGroup;
import mx.unam.icf.aulas.modules.reservations.groups.domain.ReservationGroupStatus;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Spring Data repository for {@link ReservationGroup} persistence operations.
 *
 * @author Ithera
 * @version 2.0
 */
public interface ReservationGroupRepository extends JpaRepository<ReservationGroup, Long> {

    /** Finds a reservation group by its public UUID. */
    Optional<ReservationGroup> findByUuid(UUID uuid);

    /** Returns all reservation groups belonging to a given user. */
    List<ReservationGroup> findByUserUuid(UUID userUuid);

    /**
     * Returns a page of all reservation groups with user and semester eagerly loaded.
     * Used by the paginated admin listing endpoint.
     */
    @Override
    @EntityGraph(attributePaths = {"user", "semester"})
    Page<ReservationGroup> findAll(@NonNull Pageable pageable);

    /**
     * Returns a page of reservation groups belonging to a given user.
     *
     * @param userUuid public UUID of the target user
     * @param pageable pagination and sort criteria
     */
    @EntityGraph(attributePaths = {"user", "semester"})
    Page<ReservationGroup> findByUserUuid(UUID userUuid, Pageable pageable);

    /**
     * Returns groups created before {@code threshold}. Used by cleanup tasks that
     * need to inspect groups regardless of their status (e.g. to detect missing roster files).
     *
     * @param threshold groups created before this instant are returned
     */
    List<ReservationGroup> findByCreatedAtBefore(LocalDateTime threshold);

    /**
     * Returns the public UUIDs of <em>every</em> reservation group as a single projection
     * query — no entities are hydrated.
     *
     * <p>Used by {@code StudentRosterCleanupJob}'s orphan-file sweep as an in-memory
     * snapshot: the job checks each stored roster filename against this set with O(1)
     * {@code contains} lookups instead of issuing one {@code existsByUuid} query per file
     * (an N+1 against the database when the storage folder accumulates thousands of
     * rosters). A few thousand UUIDs in a {@code HashSet} cost only KBs of heap.</p>
     *
     * @return all group UUIDs; empty when no groups exist
     */
    @Query("SELECT g.uuid FROM ReservationGroup g")
    Set<UUID> findAllUuids();
}
