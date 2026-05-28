package mx.unam.icf.aulas.modules.access.roles.infrastructure;

import mx.unam.icf.aulas.modules.access.roles.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link Role} entities.
 */
public interface RoleRepository extends JpaRepository<Role, Long> {
}

