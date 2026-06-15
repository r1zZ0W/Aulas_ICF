package mx.unam.icf.aulas.modules.access.roles.infrastructure;

import mx.unam.icf.aulas.modules.access.roles.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Role} entities.
 */
public interface RoleRepository extends JpaRepository<Role, Long> {

    /** Finds a role by its unique name (e.g. "ADMIN", "MAESTRO"). */
    Optional<Role> findByName(String name);
}

