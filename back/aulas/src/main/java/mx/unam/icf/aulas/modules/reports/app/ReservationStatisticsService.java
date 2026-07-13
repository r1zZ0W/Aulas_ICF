package mx.unam.icf.aulas.modules.reports.app;

import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.modules.reports.app.dtos.ReservationStatisticsDTO;
import mx.unam.icf.aulas.modules.reports.app.projections.ClassroomSlotsView;
import mx.unam.icf.aulas.modules.reports.app.projections.DateCountView;
import mx.unam.icf.aulas.modules.reports.app.projections.UserReservationsView;
import mx.unam.icf.aulas.modules.reports.infrastructure.ReportStatisticsRepository;
import mx.unam.icf.aulas.modules.reservations.instances.domain.ReservInstanceStatus;
import mx.unam.icf.aulas.modules.reservations.slots.app.ReservationSlotProperties;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Application service that computes all aggregated metrics for the
 * "Reportes y Estadísticas" dashboard.
 *
 * <p>Orchestrates the three collaborators:</p>
 * <ol>
 *   <li>{@link StatisticsPeriodResolver} — parses and validates the query parameters,
 *       resolves {@code [from, to]} with its comparable previous period, and builds the
 *       trend-series scaffold.</li>
 *   <li>{@link ReportStatisticsRepository} — executes aggregation queries; all metrics are
 *       computed by the database (no entity loading, no in-JVM aggregation).</li>
 *   <li>{@link ReservationSlotProperties} — provides the slot-to-hours conversion factor
 *       as a domain rule, avoiding a magic number in the query string.</li>
 * </ol>
 *
 * <p><b>Short-circuit rule:</b> if the resolver cannot determine any valid date range
 * (e.g., {@code scope=SEMESTER} on a freshly installed database with no semesters),
 * the service returns a zeroed-out DTO immediately without touching the database.
 * The {@link ReservationStatisticsDTO#trend()} list is never empty in the short-circuit
 * path — a scaffold of buckets is generated so that frontend chart components can render
 * their axes.</p>
 *
 * @see StatisticsPeriodResolver
 * @see ReportStatisticsRepository
 */
@Service
@RequiredArgsConstructor
public class ReservationStatisticsService {

    private static final int TOP_N = 5;

    private final StatisticsPeriodResolver     resolver;
    private final ReportStatisticsRepository   statsRepo;
    private final ReservationSlotProperties    slotProps;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the {@code yyyy-MM} months that have at least one active reservation, newest first.
     *
     * <p>Used to populate the MONTHLY scope's period dropdown so it only lists months with
     * actual data, instead of a fixed rolling window. Aggregation happens entirely in the
     * database ({@link ReportStatisticsRepository#findActiveYearsAndMonths}); this method only
     * formats the already-aggregated rows.</p>
     *
     * <p>Each row from the repository is a {@code [year, month]} pair. The boxed numeric type
     * returned by the JPQL {@code YEAR()}/{@code MONTH()} functions varies across JPA providers
     * and databases (e.g. {@code Integer} vs {@code Long}), so rows are read as {@link Number}
     * and narrowed with {@link Number#intValue()} rather than cast directly — a direct
     * {@code (Integer)} cast would risk a {@link ClassCastException} depending on the runtime
     * database. The month is zero-padded ({@code "06"}, not {@code "6"}) so the result matches
     * the {@code yyyy-MM} format expected by {@link StatisticsPeriodResolver#resolve} and the
     * frontend's Zod validation.</p>
     *
     * @return distinct {@code yyyy-MM} strings with at least one active reservation, newest first
     */
    @Transactional(readOnly = true)
    public List<String> availableMonths() {
        return statsRepo.findActiveYearsAndMonths(ReservInstanceStatus.ACTIVE).stream()
                .map(row -> String.format("%d-%02d",
                        ((Number) row[0]).intValue(),
                        ((Number) row[1]).intValue()))
                .toList();
    }

    /**
     * Computes the full statistics payload for the Reportes y Estadísticas dashboard.
     *
     * <p>The method is read-only transactional so all repository calls share the same
     * database snapshot and are eligible for connection-pool optimisations on supported
     * databases.</p>
     *
     * @param rawScope the {@code scope} query parameter value ({@code "MONTHLY"} or
     *                 {@code "SEMESTER"}); case-insensitive
     * @param anchor   the {@code anchor} query parameter value: a {@code yyyy-MM} string for
     *                 monthly scope, or a semester UUID string for semester scope;
     *                 {@code null} or blank triggers the period default
     * @return the aggregated statistics DTO, never {@code null}
     * @throws IllegalArgumentException if {@code scope} is unrecognised, or if {@code anchor}
     *                                  has an invalid format or references a non-existent semester
     */
    @Transactional(readOnly = true)
    public ReservationStatisticsDTO getStatistics(String rawScope, String anchor) {
        StatisticsScope scope = StatisticsScope.parse(rawScope);

        Optional<ResolvedPeriod> maybe = resolver.resolve(scope, anchor);
        if (maybe.isEmpty()) {
            // No semesters in the database — return safe zeroed-out DTO
            return emptyStatistics(scope);
        }

        ResolvedPeriod p        = maybe.get();
        double         slotHours = slotProps.slotDurationHours();

        // 1. Total reservations in the current period
        long total = statsRepo.countActive(p.from(), p.to(), ReservInstanceStatus.ACTIVE);

        // 2. Delta vs. previous comparable period
        Double delta = computeDelta(total, p);

        // 3. Classroom occupancy (top 5)
        List<ClassroomSlotsView> classroomRows = statsRepo.topClassroomsBySlots(
                p.from(), p.to(), ReservInstanceStatus.ACTIVE, Limit.of(TOP_N));
        List<ReservationStatisticsDTO.ClassroomOccupancy> mostOccupiedClassrooms = classroomRows.stream()
                .map(v -> new ReservationStatisticsDTO.ClassroomOccupancy(
                        v.getName(), v.getTotalSlots() * slotHours))
                .toList();
        ReservationStatisticsDTO.ClassroomOccupancy mostOccupiedClassroom =
                mostOccupiedClassrooms.isEmpty() ? null : mostOccupiedClassrooms.getFirst();

        // 4. User rankings (top 5)
        List<UserReservationsView> userRows = statsRepo.topUsersByReservations(
                p.from(), p.to(), ReservInstanceStatus.ACTIVE, Limit.of(TOP_N));
        List<ReservationStatisticsDTO.UserReservations> topUsers = userRows.stream()
                .map(v -> new ReservationStatisticsDTO.UserReservations(v.getName(), v.getReservations()))
                .toList();
        ReservationStatisticsDTO.UserReservations topUser =
                topUsers.isEmpty() ? null : topUsers.getFirst();

        // 5. Recurrence — counted by reservation (group), not by session-day. A weekly class
        // with 12 sessions contributes 1 to the recurring count, not 12; see countInstancesPerGroup.
        List<Long> instancesPerGroup = statsRepo.countInstancesPerGroup(
                p.from(), p.to(), ReservInstanceStatus.ACTIVE);
        long recurring = instancesPerGroup.stream().filter(c -> c > 1).count();
        long oneTime   = instancesPerGroup.size() - recurring;
        double rate    = instancesPerGroup.isEmpty()
                ? 0.0 : recurring * 100.0 / instancesPerGroup.size();

        // 6. Trend series — merge sparse DB rows into the complete scaffold
        List<DateCountView> dateRows = statsRepo.countPerDate(
                p.from(), p.to(), ReservInstanceStatus.ACTIVE);
        List<ReservationStatisticsDTO.TrendPoint> trend =
                buildTrend(scope, p.trendLabels(), dateRows);

        return new ReservationStatisticsDTO(
                total,
                delta,
                mostOccupiedClassroom,
                topUser,
                rate,
                mostOccupiedClassrooms,
                topUsers,
                new ReservationStatisticsDTO.Recurrence(recurring, oneTime),
                trend
        );
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Computes the percentage change in reservation count relative to the comparable previous
     * period. The previous period's date range is already proportionally truncated by
     * {@link StatisticsPeriodResolver#computePrevTo}, so both counts cover comparable spans.
     *
     * @param actual total reservations in the current period
     * @param p      the resolved period (carries {@code prevFrom}/{@code prevTo})
     * @return the delta percentage, or {@code null} when the previous period had zero
     *         reservations or no previous period exists
     */
    private Double computeDelta(long actual, ResolvedPeriod p) {

        if (p.prevFrom() == null)
            return null;    // No previous period exists (e.g., first semester in the database)

        long prev = statsRepo.countActive(p.prevFrom(), p.prevTo(), ReservInstanceStatus.ACTIVE);
        if (prev == 0)
            return null;    // Avoid division by zero; frontend renders no delta badge

        return (actual - prev) * 100.0 / prev;
    }

    /**
     * Merges the sparse date-count rows from the repository into the complete scaffold.
     *
     * <p>For {@code SEMESTER} scope, multiple dates may fall in the same calendar month and
     * are accumulated using {@link Long#sum}. For {@code MONTHLY} scope, each date maps to a
     * unique day label.</p>
     *
     * <p>{@code SEMESTER} labels only include the year when the scaffold spans more than 12
     * months (see {@link StatisticsPeriodResolver#buildMonthScaffold}) — otherwise two dates a
     * year apart (e.g. two Januaries, for a mis-configured multi-year semester) would collapse
     * onto the same {@code "Ene"} key and their counts would be summed together instead of kept
     * as separate buckets. The {@code includeYear} flag here must mirror the one used to build
     * the scaffold so the merge key always matches a scaffold label.</p>
     *
     * @param scope      determines how each date is converted to a bucket label
     * @param scaffold   complete ordered list of labels (including future/empty buckets)
     * @param dateRows   sparse list of (date, count) rows from the database
     * @return complete trend list; one entry per scaffold label, zero for empty buckets
     */
    private List<ReservationStatisticsDTO.TrendPoint> buildTrend(
            StatisticsScope scope,
            List<String> scaffold,
            List<DateCountView> dateRows) {

        boolean includeYear = scope == StatisticsScope.SEMESTER && scaffold.size() > 12;

        Map<String, Long> labelMap = new HashMap<>();
        for (DateCountView row : dateRows) {
            String label = scope.trendLabel(row.getDate(), includeYear);
            labelMap.merge(label, row.getTotal(), Long::sum);
        }

        return scaffold.stream()
                .map(label -> new ReservationStatisticsDTO.TrendPoint(
                        label, labelMap.getOrDefault(label, 0L)))
                .toList();
    }

    /**
     * Returns a zeroed-out statistics DTO for use when no date range can be determined
     * (e.g., SEMESTER scope with no semesters in the database).
     *
     * <p>The {@code trend} list is <em>never</em> empty: a scaffold of zero-valued buckets
     * is generated so that frontend chart components can render their axes without encountering
     * an empty array and throwing a runtime error.</p>
     *
     * <ul>
     *   <li>{@code MONTHLY}: scaffold contains every day of the current month.</li>
     *   <li>{@code SEMESTER}: scaffold contains the last 6 calendar months up to and
     *       including the current month, labelled with es-MX month abbreviations.</li>
     * </ul>
     *
     * @param scope the requested scope, used to build an appropriate trend scaffold
     * @return a fully-initialised DTO with all numeric fields set to zero/null
     */
    private ReservationStatisticsDTO emptyStatistics(StatisticsScope scope) {
        List<ReservationStatisticsDTO.TrendPoint> trend;

        if (scope == StatisticsScope.MONTHLY) {
            YearMonth ym = YearMonth.now();
            trend = IntStream.rangeClosed(1, ym.lengthOfMonth())
                    .mapToObj(d -> new ReservationStatisticsDTO.TrendPoint(
                            String.format("%02d", d), 0L))
                    .toList();
        } else {
            // SEMESTER: last 6 months (month 0 = 5 months ago, month 5 = current)
            YearMonth current = YearMonth.now();
            trend = IntStream.range(0, 6)
                    .mapToObj(i -> {
                        LocalDate d = current.minusMonths(5 - i).atDay(1);
                        return new ReservationStatisticsDTO.TrendPoint(
                                StatisticsScope.SEMESTER.trendLabel(d), 0L);
                    })
                    .toList();
        }

        return new ReservationStatisticsDTO(
                0L,
                null,
                null,
                null,
                0.0,
                List.of(),
                List.of(),
                new ReservationStatisticsDTO.Recurrence(0L, 0L),
                trend
        );
    }
}
