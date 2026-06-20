package mx.unam.icf.aulas.modules.academic.semesters.infrastructure;

import mx.unam.icf.aulas.modules.academic.semesters.domain.Semester;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link Semester} persistence operations.
 *
 * <p>The concept of an "active" semester is derived from dates, not from a persisted column.
 * Use {@link #findCurrent(LocalDate)} to retrieve the operationally active semester for a
 * given reference date.</p>
 *
 * @author Ithera
 * @version 2.0
 */
public interface SemesterRepository extends JpaRepository<Semester, Long> {

    /**
     * Finds a semester by its unique name (e.g., {@code "2026-1"}).
     *
     * @param name the semester name to search for
     * @return an {@link Optional} containing the matching semester, or empty if not found
     */
    Optional<Semester> findByName(String name);

    /**
     * Retrieves a semester by its public UUID.
     *
     * @param uuid the external identifier of the semester
     * @return an {@link Optional} containing the matching semester, or empty if not found
     */
    Optional<Semester> findByUuid(UUID uuid);

    /**
     * Returns the single operationally current semester: the one with the latest
     * {@code endDate} whose inclusive range {@code [startDate, endDate]} contains
     * the given reference date.
     *
     * <p>Ordering by {@code endDate DESC} and limiting to 1 row (via Spring Data's
     * {@code findFirst} prefix) ensures a deterministic result even if overlapping
     * date ranges exist in the data due to a manual data-entry mistake. The returned
     * semester is the one that expires last, reflecting the most recent academic period.</p>
     *
     * @param startRef reference date bound to the {@code startDate <=} predicate
     * @param endRef   same reference date bound to the {@code endDate >=} predicate
     * @return the current semester, or {@link Optional#empty()} if none covers the date
     */
    Optional<Semester> findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByEndDateDesc(
            LocalDate startRef, LocalDate endRef);

    /**
     * Convenience alias for {@link #findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByEndDateDesc}.
     * Pass the same {@code today} value for both parameters.
     *
     * @param today reference date representing "now"
     * @return the current semester, or empty if none is active today
     */
    default Optional<Semester> findCurrent(LocalDate today) {
        return findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByEndDateDesc(today, today);
    }
}
