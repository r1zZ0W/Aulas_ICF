package mx.unam.icf.aulas.modules.resources.allocations.infrastructure;

import mx.unam.icf.aulas.modules.resources.allocations.domain.ClassroomResource;
import mx.unam.icf.aulas.modules.resources.allocations.domain.ClassroomResourceId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Spring Data repository for {@link ClassroomResource} (classroom–equipment allocation) operations.
 *
 * @author Ithera
 * @version 1.0
 */
public interface ClassroomResourceRepository extends JpaRepository<ClassroomResource, ClassroomResourceId> {

    /** Returns all equipment allocations for a given classroom by its internal ID. */
    @Query("SELECT cr FROM ClassroomResource cr WHERE cr.classroom.id = :classroomId")
    List<ClassroomResource> findByClassroomId(@Param("classroomId") Long classroomId);
}
