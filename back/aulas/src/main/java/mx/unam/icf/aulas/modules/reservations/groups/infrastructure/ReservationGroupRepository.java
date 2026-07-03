package mx.unam.icf.aulas.modules.reservations.groups.infrastructure;

import mx.unam.icf.aulas.modules.reservations.groups.domain.ReservationGroup;
import mx.unam.icf.aulas.modules.reservations.groups.domain.ReservationGroupStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
    Page<ReservationGroup> findAll(Pageable pageable);

    /**
     * Returns a page of reservation groups belonging to a given user.
     *
     * @param userUuid public UUID of the target user
     * @param pageable pagination and sort criteria
     */
    @EntityGraph(attributePaths = {"user", "semester"})
    Page<ReservationGroup> findByUserUuid(UUID userUuid, Pageable pageable);

    /**
     * Returns groups stuck in a given status whose creation timestamp is older than
     * {@code threshold}. Used by {@code StudentRosterCleanupJob} to find
     * {@link ReservationGroupStatus#PENDING_ROSTER} groups abandoned past the grace
     * period (their student roster was never uploaded).
     *
     * @param status    the status to filter by (e.g. {@code PENDING_ROSTER})
     * @param threshold groups created before this instant are considered abandoned
     */
    List<ReservationGroup> findByStatusAndCreatedAtBefore(ReservationGroupStatus status, LocalDateTime threshold);
}
