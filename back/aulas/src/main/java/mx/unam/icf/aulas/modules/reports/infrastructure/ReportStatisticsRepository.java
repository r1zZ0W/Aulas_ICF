package mx.unam.icf.aulas.modules.reports.infrastructure;

import mx.unam.icf.aulas.modules.reports.app.projections.ClassroomSlotsView;
import mx.unam.icf.aulas.modules.reports.app.projections.DateCountView;
import mx.unam.icf.aulas.modules.reports.app.projections.UserReservationsView;
import mx.unam.icf.aulas.modules.reservations.instances.domain.ReservInstance;
import mx.unam.icf.aulas.modules.reservations.instances.domain.ReservInstanceStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * Read-only aggregation repository for the "Reportes y Estadísticas" dashboard.
 *
 * <p>All methods issue a single aggregated query to the database; no entities are loaded into
 * the persistence context and no in-memory aggregation is performed. This is intentional:
 * the domain model for reservations is large and eager-loading entities for counting purposes
 * would cause severe N+1 issues at the scale of a full semester's worth of bookings.</p>
 *
 * <p>This repository is kept separate from
 * {@link mx.unam.icf.aulas.modules.reservations.instances.infrastructure.ReservInstanceRepository}
 * to preserve the cohesion of the reservations module and to allow reporting queries to evolve
 * independently.</p>
 *
 * <p>All queries filter by {@link ReservInstanceStatus} to allow the caller to choose the status
 * universe; pass {@link ReservInstanceStatus#ACTIVE} for live dashboard metrics.</p>
 */
public interface ReportStatisticsRepository extends JpaRepository<ReservInstance, Long> {

    // ── Total counts ──────────────────────────────────────────────────────────

    /**
     * Counts active reservation instances within a date range.
     *
     * <p>Used both for the current period ({@code totalReservations} KPI) and for the previous
     * comparable period (delta calculation). The previous period's range is already truncated
     * proportionally by {@link mx.unam.icf.aulas.modules.reports.app.StatisticsPeriodResolver}
     * so both calls receive date ranges of comparable length.</p>
     *
     * @param from   start of the date range (inclusive)
     * @param to     end of the date range (inclusive)
     * @param status status to filter by; use {@link ReservInstanceStatus#ACTIVE}
     * @return number of matching reservation instances; {@code 0} if none
     */
    @Query("SELECT COUNT(ri) FROM ReservInstance ri " +
           "WHERE ri.status = :status " +
           "AND ri.date BETWEEN :from AND :to")
    long countActive(
            @Param("from")   LocalDate from,
            @Param("to")     LocalDate to,
            @Param("status") ReservInstanceStatus status
    );

    // ── Classroom occupancy ───────────────────────────────────────────────────

    /**
     * Returns the top-N classrooms ordered by slot count (descending) within a date range.
     *
     * <p>Returns <em>slot counts</em>, not hours, because the duration of a slot is a domain
     * rule ({@link mx.unam.icf.aulas.modules.reservations.slots.app.ReservationSlotProperties})
     * that must not be embedded as a numeric literal inside a JPQL string. The caller converts
     * {@code totalSlots × slotDurationHours} to the {@code horas} field of the DTO.</p>
     *
     * @param from   start of the date range (inclusive)
     * @param to     end of the date range (inclusive)
     * @param status status filter; use {@link ReservInstanceStatus#ACTIVE}
     * @param limit  maximum number of rows to return (use {@code Limit.of(5)} for the top-5 KPI)
     * @return list of {@link ClassroomSlotsView} projections, ordered most-occupied first
     */
    @Query("SELECT c.name AS name, COUNT(rs) AS totalSlots " +
           "FROM ReservSlot rs " +
           "JOIN rs.instance ri " +
           "JOIN ri.classroom c " +
           "WHERE ri.status = :status " +
           "AND ri.date BETWEEN :from AND :to " +
           "GROUP BY c.id, c.name " +
           "ORDER BY COUNT(rs) DESC")
    List<ClassroomSlotsView> topClassroomsBySlots(
            @Param("from")   LocalDate from,
            @Param("to")     LocalDate to,
            @Param("status") ReservInstanceStatus status,
            Limit limit
    );

    // ── User rankings ─────────────────────────────────────────────────────────

    /**
     * Returns the top-N users ordered by reservation count (descending) within a date range.
     *
     * <p>The full name ({@code AS name}) is sanitized in the query using
     * {@code TRIM(CONCAT(firstName, ' ', COALESCE(lastNames, '')))}: the {@code COALESCE}
     * prevents MySQL from returning {@code NULL} for the entire concatenation when
     * {@code lastNames} is {@code NULL}, and {@code TRIM} removes the trailing space that
     * would otherwise appear for users without a recorded last name.</p>
     *
     * @param from   start of the date range (inclusive)
     * @param to     end of the date range (inclusive)
     * @param status status filter; use {@link ReservInstanceStatus#ACTIVE}
     * @param limit  maximum number of rows to return (use {@code Limit.of(5)} for the top-5 KPI)
     * @return list of {@link UserReservationsView} projections, ordered highest-count first
     */
    @Query("SELECT TRIM(CONCAT(u.firstName, ' ', COALESCE(u.lastNames, ''))) AS name, " +
           "COUNT(ri) AS reservations " +
           "FROM ReservInstance ri " +
           "JOIN ri.group g " +
           "JOIN g.user u " +
           "WHERE ri.status = :status " +
           "AND ri.date BETWEEN :from AND :to " +
           "GROUP BY u.id, u.firstName, u.lastNames " +
           "ORDER BY COUNT(ri) DESC")
    List<UserReservationsView> topUsersByReservations(
            @Param("from")   LocalDate from,
            @Param("to")     LocalDate to,
            @Param("status") ReservInstanceStatus status,
            Limit limit
    );

    // ── Recurrence ────────────────────────────────────────────────────────────

    /**
     * Returns, for every {@code ReservationGroup} that has at least one active instance in the
     * given date range, the number of active instances that group has <em>within that same
     * range</em> — one row per group (the {@code GROUP BY} key is never selected, only its
     * per-group count).
     *
     * <p>The caller ({@link mx.unam.icf.aulas.modules.reports.app.ReservationStatisticsService})
     * classifies each row: a group with {@code count > 1} is <em>recurring</em> (a booking that
     * repeats within the period, e.g. a weekly class with several sessions this month); a group
     * with {@code count == 1} is <em>one-time</em>. This counts <b>reservations (groups)</b>, not
     * instances — a 12-session weekly class contributes exactly {@code 1} to the recurring count, not 12.</p>
     *
     * <p>A single aggregated {@code GROUP BY} query is used instead of a correlated {@code EXISTS}
     * subquery per row: it performs one range scan over {@code [from, to]} and lets the database
     * aggregate directly, which scales better than re-evaluating a subquery for every instance.
     * Recommended for a future migration: a composite index on
     * {@code reserv_instances(status, date, group_id)} would let this query resolve entirely from
     * the index (range-scan on {@code status + date}, then group by {@code group_id}) instead of
     * falling back to a table scan for the date predicate.</p>
     *
     * @param from   start of the date range (inclusive)
     * @param to     end of the date range (inclusive)
     * @param status status filter; use {@link ReservInstanceStatus#ACTIVE}
     * @return one entry per distinct group with instances in the range, each holding that
     *         group's instance count within the range; empty when the period has no reservations
     */
    @Query("SELECT COUNT(ri.id) FROM ReservInstance ri " +
           "WHERE ri.status = :status " +
           "AND ri.date BETWEEN :from AND :to " +
           "GROUP BY ri.group.id")
    List<Long> countInstancesPerGroup(
            @Param("from")   LocalDate from,
            @Param("to")     LocalDate to,
            @Param("status") ReservInstanceStatus status
    );

    // ── Available months ──────────────────────────────────────────────────────

    /**
     * Returns the distinct (year, month) pairs that have at least one active reservation,
     * ordered most-recent first.
     *
     * <p>Aggregation happens entirely in the database via the portable JPQL {@code YEAR()}/
     * {@code MONTH()} functions — no per-date rows are pulled into the JVM. Each row is a
     * two-element {@code Object[]}: {@code [0] = year}, {@code [1] = month} (1-based). The
     * caller ({@link mx.unam.icf.aulas.modules.reports.app.ReservationStatisticsService}) must
     * read these as {@link Number} (not cast directly to {@code Integer}) since the boxed type
     * returned for numeric JPQL functions varies across JPA providers/databases.</p>
     *
     * @param status status filter; use {@link ReservInstanceStatus#ACTIVE}
     * @return distinct year/month pairs with at least one matching reservation, newest first
     */
    @Query("SELECT DISTINCT YEAR(ri.date) AS y, MONTH(ri.date) AS m " +
           "FROM ReservInstance ri WHERE ri.status = :status " +
           "ORDER BY y DESC, m DESC")
    List<Object[]> findActiveYearsAndMonths(@Param("status") ReservInstanceStatus status);

    // ── Trend series ──────────────────────────────────────────────────────────

    /**
     * Returns one row per distinct date that has at least one active reservation in the range.
     *
     * <p>The result is intentionally sparse (no rows for dates with zero reservations). The
     * caller fills in zero-count buckets by iterating over the full scaffold from
     * {@link mx.unam.icf.aulas.modules.reports.app.ResolvedPeriod#trendLabels()} and
     * looking each label up in the map derived from these rows.</p>
     *
     * <p>Using {@code ri.date} for grouping (a portable JPQL path) rather than MySQL-specific
     * functions like {@code DAY()} or {@code MONTH()} keeps the query database-agnostic and
     * allows the service to handle both monthly (day labels) and semester (month labels)
     * bucketing with the same query.</p>
     *
     * @param from   start of the date range (inclusive)
     * @param to     end of the date range (inclusive)
     * @param status status filter; use {@link ReservInstanceStatus#ACTIVE}
     * @return list of {@link DateCountView} projections, one per distinct date, sorted ascending
     */
    @Query("SELECT ri.date AS date, COUNT(ri) AS total " +
           "FROM ReservInstance ri " +
           "WHERE ri.status = :status " +
           "AND ri.date BETWEEN :from AND :to " +
           "GROUP BY ri.date " +
           "ORDER BY ri.date ASC")
    List<DateCountView> countPerDate(
            @Param("from")   LocalDate from,
            @Param("to")     LocalDate to,
            @Param("status") ReservInstanceStatus status
    );
}
