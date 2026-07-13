package mx.unam.icf.aulas.modules.reports.app.projections;

/**
 * Spring Data interface projection for user reservation-count aggregation queries.
 *
 * <p>Returned by {@code ReportStatisticsRepository.topUsersByReservations()}. Each instance
 * represents one user and the count of active {@code ReservInstance} records they hold within
 * the queried date range.</p>
 *
 * <p>The {@code name} value is produced by the query as
 * {@code TRIM(CONCAT(firstName, ' ', COALESCE(lastNames, '')))}
 * to guard against {@code NULL} last-names propagating through {@code CONCAT} in MySQL,
 * and to strip the trailing space that would appear when {@code lastNames} is absent.</p>
 *
 * <p><b>Getter names must mirror the JPQL aliases</b> ({@code AS name}, {@code AS reservations})
 * in the repository query — Spring Data resolves interface projections by alias at runtime,
 * so a mismatch fails on execution, not compilation.</p>
 */
public interface UserReservationsView {

    /**
     * Returns the user's display name, sanitized by the query:
     * {@code TRIM(CONCAT(firstName, ' ', COALESCE(lastNames, '')))}.
     *
     * @return non-null, trimmed full name (e.g., {@code "María García"} or {@code "Daniel"})
     */
    String getName();

    /**
     * Returns the total number of active {@code ReservInstance} records owned by this user
     * within the analysis period.
     *
     * @return reservation count; always {@code >= 0}
     */
    long getReservations();
}
