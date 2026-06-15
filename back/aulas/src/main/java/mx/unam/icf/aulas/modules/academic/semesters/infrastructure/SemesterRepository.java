package mx.unam.icf.aulas.modules.academic.semesters.infrastructure;

import mx.unam.icf.aulas.modules.academic.semesters.domain.Semester;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link Semester} persistence operations.
 *
 * @author Ithera
 * @version 1.0
 */
public interface SemesterRepository extends JpaRepository<Semester, Long> {

    /** Finds a semester by its unique name (e.g., "2026-1"). */
    Optional<Semester> findByName(String name);

    /** Returns all semesters currently marked as active. */
    List<Semester> findByIsActiveTrue();
}
