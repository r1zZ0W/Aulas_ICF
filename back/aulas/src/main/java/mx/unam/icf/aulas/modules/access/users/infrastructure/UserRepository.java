package mx.unam.icf.aulas.modules.access.users.infrastructure;

import mx.unam.icf.aulas.modules.access.users.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link User} entities.
 *
 * @author Ithera
 * @version 1.0
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /** Finds a user by email, ignoring case, for authentication lookups. */
    Optional<User> findByEmailIgnoreCase(String email);

    /** Finds a user by username, ignoring case, for uniqueness checks. */
    Optional<User> findByUsernameIgnoreCase(String username);

    /** Finds a user by their public UUID for external API lookups. */
    Optional<User> findByUuid(UUID uuid);
}
