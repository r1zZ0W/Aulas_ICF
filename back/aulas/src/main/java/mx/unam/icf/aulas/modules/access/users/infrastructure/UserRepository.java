package mx.unam.icf.aulas.modules.access.users.infrastructure;

import mx.unam.icf.aulas.modules.access.users.app.dtos.UserStatsDTO;
import mx.unam.icf.aulas.modules.access.users.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Returns a page of all users with the {@code role} association eagerly loaded.
     * Used by the paginated admin listing endpoint (no-search path).
     */
    @Override
    @EntityGraph(attributePaths = "role")
    Page<User> findAll(Pageable pageable);

    /**
     * Full-text search across user-visible fields.
     *
     * <p>Performs a case-insensitive {@code LIKE} match on {@code firstName},
     * {@code lastNames}, {@code email}, {@code username}, and {@code matricula}.
     * The {@code role} association is eagerly joined to avoid N+1 reads when the
     * mapper accesses {@code role.name}. Spring Data derives the count query
     * automatically from the {@code SELECT} clause.</p>
     *
     * @param q        search term (already trimmed by the caller; never {@code null})
     * @param pageable pagination and sort criteria
     * @return a page of matching users
     */
    @EntityGraph(attributePaths = "role")
    @Query("""
            SELECT u FROM User u
            WHERE LOWER(u.firstName) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(u.lastNames) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(u.email)     LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(u.username)  LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(u.matricula) LIKE LOWER(CONCAT('%', :q, '%'))
            """)
    Page<User> search(@Param("q") String q, Pageable pageable);

    /**
     * Returns a single-row aggregate with counts for the admin stats dashboard.
     *
     * <p>Resolves all four counters in one database round-trip using a JPQL
     * constructor expression, without materializing the full user list.</p>
     *
     * @return a {@link UserStatsDTO} with total / active / inactive / admin counts
     */
    @Query("""
            SELECT new mx.unam.icf.aulas.modules.access.users.app.dtos.UserStatsDTO(
                COUNT(u),
                SUM(CASE WHEN u.isActive = true  THEN 1L ELSE 0L END),
                SUM(CASE WHEN u.isActive = false OR u.isActive IS NULL THEN 1L ELSE 0L END),
                SUM(CASE WHEN u.role.name = 'ADMIN' THEN 1L ELSE 0L END))
            FROM User u
            """)
    UserStatsDTO fetchStats();
}
