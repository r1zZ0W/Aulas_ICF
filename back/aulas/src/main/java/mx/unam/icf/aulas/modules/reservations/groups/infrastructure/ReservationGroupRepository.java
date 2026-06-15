package mx.unam.icf.aulas.modules.reservations.groups.infrastructure;

import mx.unam.icf.aulas.modules.reservations.groups.domain.ReservationGroup;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
