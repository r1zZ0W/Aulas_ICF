package mx.unam.icf.aulas.modules.access.users.infrastructure;

import mx.unam.icf.aulas.modules.access.users.domain.User;
import org.springframework.data.jpa.repository.EntityGraph;
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

    /**
     * Finds a user by username for Spring Security's authentication process.
     * The {@code role} association is eagerly joined in the same query so that
     * {@code UserDetailsImp} can read {@code role.getName()} after the session closes
     * (the security filter chain runs outside the Open-Session-In-View scope).
     */
    @EntityGraph(attributePaths = "role")
    Optional<User> findByUsernameIgnoreCase(String username);

    /**
     * Finds a user by their public UUID, used on every authenticated request
     * to verify the account is still active.
     * The {@code role} association is eagerly joined in the same query for the
     * same reason as {@link #findByUsernameIgnoreCase}.
     */
    @EntityGraph(attributePaths = "role")
    Optional<User> findByUuid(UUID uuid);

    /** Finds a user by their unique matrícula. */
    Optional<User> findByMatricula(String matricula);

    /**
     * Returns all active users assigned to a specific role by role name.
     * Used to resolve admin recipients for reservation notifications.
     */
    java.util.List<User> findByRole_NameAndIsActiveTrue(String roleName);

    /** Returns true if at least one user with the given role name exists (used by AdminSeeder). */
    boolean existsByRole_Name(String roleName);
}
