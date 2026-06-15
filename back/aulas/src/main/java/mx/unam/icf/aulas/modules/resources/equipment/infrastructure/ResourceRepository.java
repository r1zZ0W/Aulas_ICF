package mx.unam.icf.aulas.modules.resources.equipment.infrastructure;

import mx.unam.icf.aulas.modules.resources.equipment.domain.Resource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data repository for {@link Resource} (equipment catalog) persistence operations.
 *
 * @author Ithera
 * @version 1.0
 */
public interface ResourceRepository extends JpaRepository<Resource, Integer> {

    /** Finds an equipment resource by its unique name (case-sensitive). */
    Optional<Resource> findByName(String name);
}
