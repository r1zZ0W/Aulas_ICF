package mx.unam.icf.aulas.modules.resources.equipment.infrastructure;

import mx.unam.icf.aulas.modules.resources.equipment.app.dtos.ResourceStatsDTO;
import mx.unam.icf.aulas.modules.resources.equipment.domain.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link Resource} (equipment catalog) persistence operations.
 *
 * @author Ithera
 * @version 2.0
 */
public interface ResourceRepository extends JpaRepository<Resource, Integer> {

    /** Finds an equipment resource by its public UUID. */
    Optional<Resource> findByUuid(UUID uuid);

    /** Finds an equipment resource by its unique name (case-sensitive). */
    Optional<Resource> findByName(String name);

    /**
     * Full-text search across the catalog. Performs a case-insensitive {@code LIKE}
     * match on {@code name} and {@code description}.
     *
     * @param q        search term (already trimmed by the caller; never {@code null})
     * @param pageable pagination and sort criteria
     * @return a page of matching resources
     */
    @Query("""
            SELECT r FROM Resource r
            WHERE (LOWER(r.name)        LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(r.description) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Resource> search(@Param("q") String q, Pageable pageable);

    /**
     * Returns a single-row aggregate with counts for the admin stats dashboard.
     *
     * <p>Resolves both counters in one database round-trip using a JPQL constructor
     * expression, without materializing the full resource list. {@code COALESCE}
     * guards against {@code NULL} when the table is empty.</p>
     *
     * @return a {@link ResourceStatsDTO} with totalTypes / totalUnits counts
     */
    @Query("""
            SELECT new mx.unam.icf.aulas.modules.resources.equipment.app.dtos.ResourceStatsDTO(
                COUNT(r),
                COALESCE(SUM(r.quantity), 0L))
            FROM Resource r
            """)
    ResourceStatsDTO fetchStats();
}
