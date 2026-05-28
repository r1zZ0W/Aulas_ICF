package mx.unam.icf.aulas.modules.resources.classrooms.infrastructure;

import mx.unam.icf.aulas.modules.resources.classrooms.domain.Classroom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Classroom entity data access operations.
 *
 * This interface extends {@link JpaRepository} to provide standard CRUD (Create, Read, Update, Delete)
 * operations for classroom entities. It leverages Spring Data JPA to automatically generate the
 * implementation at runtime, enabling object-relational mapping to the database.
 *
 * The repository manages persistence for {@link Classroom} entities with Long as the identifier type.
 *
 * @author Ithera
 * @version 1.0
 * @see Classroom
 */
@Repository
public interface ClassroomRepository extends JpaRepository<Classroom, Long> {

    /**
     * Finds a classroom by its public UUID.
     *
     * @param uuid the public UUID to search by
     * @return an optional classroom when found
     */
    Optional<Classroom> findByUuid(UUID uuid);

    Optional<Classroom> findByName(String name);
}
