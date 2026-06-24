package mx.unam.icf.aulas.modules.reservations.history.infrastructure;

import mx.unam.icf.aulas.modules.reservations.history.domain.ReservationHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link ReservationHistory} entities.
 *
 * <p>All paginated finders use {@code @EntityGraph} to eagerly load the
 * {@code instance}, {@code group}, and {@code performedBy} associations in a
 * single JOIN query, avoiding N+1 selects during list rendering.</p>
 *
 * @author Ithera
 * @version 1.0
 */
public interface ReservationHistoryRepository extends JpaRepository<ReservationHistory, Long> {

    /**
     * Looks up a history record by its public UUID.
     *
     * @param uuid the public identifier
     * @return the matching record, if any
     */
    Optional<ReservationHistory> findByUuid(UUID uuid);

    /**
     * Returns all history records associated with a given reservation instance,
     * newest first, as a paginated result.
     *
     * @param instanceUuid public UUID of the {@link mx.unam.icf.aulas.modules.reservations.instances.domain.ReservInstance}
     * @param pageable     pagination and sort criteria
     * @return page of history records for the given instance
     */
    @EntityGraph(attributePaths = {"instance", "group", "performedBy"})
    Page<ReservationHistory> findByInstanceUuid(UUID instanceUuid, Pageable pageable);

    /**
     * Returns all history records associated with a given reservation group,
     * newest first, as a paginated result.
     *
     * @param groupUuid public UUID of the {@link mx.unam.icf.aulas.modules.reservations.groups.domain.ReservationGroup}
     * @param pageable  pagination and sort criteria
     * @return page of history records for the given group
     */
    @EntityGraph(attributePaths = {"instance", "group", "performedBy"})
    Page<ReservationHistory> findByGroupUuid(UUID groupUuid, Pageable pageable);
}
