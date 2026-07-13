package mx.unam.icf.aulas.modules.reports.app;

import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.modules.academic.semesters.domain.Semester;
import mx.unam.icf.aulas.modules.academic.semesters.infrastructure.SemesterRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring component that resolves a ({@link StatisticsScope}, anchor) pair into a fully-computed
 * {@link ResolvedPeriod}, ready for consumption by {@link ReservationStatisticsService}.
 *
 * <h3>Responsibilities</h3>
 * <ol>
 *   <li>Parse and validate the {@code anchor} parameter for each scope.</li>
 *   <li>Derive {@code [from, to]}, capping {@code to} at {@code LocalDate.now()} so that future
 *       dates (within the natural period) are never included.</li>
 *   <li>Derive the previous comparable period with proportional truncation when the current
 *       period is still open (see {@link #computePrevTo}).</li>
 *   <li>Build the ordered scaffold of trend-series labels (one per day or per month).</li>
 *   <li>Return {@link Optional#empty()} <em>only</em> when scope is {@code SEMESTER} and no
 *       semesters exist in the database, signalling the service to short-circuit.</li>
 * </ol>
 *
 * <p>All validation errors are thrown as {@link IllegalArgumentException}; the
 * {@link mx.unam.icf.aulas.kernel.infrastructure.exceptions.GlobalExceptionHandler} maps them
 * to HTTP 400. Error messages follow the handler's convention (English, terse); the frontend
 * translates them for display.</p>
 */
@Component
@RequiredArgsConstructor
public class StatisticsPeriodResolver {

    private final SemesterRepository semesterRepository;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Resolves the analysis period for the given scope and anchor.
     *
     * @param scope  the analysis granularity ({@code MONTHLY} or {@code SEMESTER})
     * @param anchor the period anchor:
     *               <ul>
     *                 <li>{@code MONTHLY}: a {@code yyyy-MM} string; {@code null}/blank → current month</li>
     *                 <li>{@code SEMESTER}: a semester UUID string; {@code null}/blank → current semester
     *                     (or latest by end date if none is currently active)</li>
     *               </ul>
     * @return the resolved period, or {@link Optional#empty()} if scope is {@code SEMESTER}
     *         and no semesters exist in the database at all
     * @throws IllegalArgumentException if the anchor format is invalid, or a SEMESTER UUID
     *                                  does not match any existing semester
     */
    public Optional<ResolvedPeriod> resolve(StatisticsScope scope, String anchor) {
        LocalDate today = LocalDate.now();
        return switch (scope) {
            case MONTHLY  -> Optional.of(resolveMonthly(anchor, today));
            case SEMESTER -> resolveSemesterPeriod(anchor, today);
        };
    }

    // ── MONTHLY ───────────────────────────────────────────────────────────────

    /**
     * Resolves a monthly analysis period.
     *
     * @param anchor raw anchor value ({@code yyyy-MM}), or {@code null}/blank for current month
     * @param today  reference date representing today
     * @return resolved monthly period
     * @throws IllegalArgumentException if the anchor is present but not a valid {@code yyyy-MM}
     */
    private ResolvedPeriod resolveMonthly(String anchor, LocalDate today) {
        YearMonth ym = parseYearMonth(anchor, today);

        LocalDate from       = ym.atDay(1);
        LocalDate naturalEnd = ym.atEndOfMonth();
        LocalDate to         = naturalEnd.isBefore(today) ? naturalEnd : today;

        // Previous period: the immediately preceding calendar month
        YearMonth prevYm         = ym.minusMonths(1);
        LocalDate prevFrom       = prevYm.atDay(1);
        LocalDate prevNaturalEnd = prevYm.atEndOfMonth();
        LocalDate prevTo         = computePrevTo(from, to, naturalEnd, prevFrom, prevNaturalEnd);

        return new ResolvedPeriod(
                StatisticsScope.MONTHLY,
                from, to,
                prevFrom, prevTo,
                buildDayScaffold(ym)
        );
    }

    /**
     * Parses a {@code yyyy-MM} anchor string, defaulting to the current month when absent.
     *
     * @param anchor raw anchor value; {@code null} or blank → current month
     * @param today  reference date for default resolution
     * @return the resolved {@link YearMonth}
     * @throws IllegalArgumentException if the string is present but cannot be parsed as {@code yyyy-MM}
     */
    private YearMonth parseYearMonth(String anchor, LocalDate today) {
        if (anchor == null || anchor.isBlank()) {
            return YearMonth.from(today);
        }
        try {
            return YearMonth.parse(anchor.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid anchor for scope MONTHLY");
        }
    }

    /**
     * Builds the complete ordered list of day labels ({@code "01"…last day}) for a monthly scaffold.
     *
     * @param ym the month whose days form the scaffold
     * @return list of zero-padded day strings, e.g. {@code ["01","02",…,"30"]}
     */
    private List<String> buildDayScaffold(YearMonth ym) {
        List<String> labels = new ArrayList<>(ym.lengthOfMonth());
        for (int d = 1; d <= ym.lengthOfMonth(); d++) {
            labels.add(String.format("%02d", d));
        }
        return labels;
    }

    // ── SEMESTER ──────────────────────────────────────────────────────────────

    /**
     * Resolves a semester analysis period.
     *
     * @param anchor raw anchor value (semester UUID string), or {@code null}/blank for default
     * @param today  reference date for current-semester lookup
     * @return resolved semester period, or {@link Optional#empty()} if no semesters exist
     * @throws IllegalArgumentException if the anchor is present but not a valid UUID,
     *                                  or if the UUID does not match any semester
     */
    private Optional<ResolvedPeriod> resolveSemesterPeriod(String anchor, LocalDate today) {
        Semester sem = resolveSemester(anchor, today);
        if (sem == null) {
            // No semesters in the database — short-circuit to empty DTO
            return Optional.empty();
        }

        LocalDate from       = sem.getStartDate();
        LocalDate naturalEnd = sem.getEndDate();
        LocalDate to         = naturalEnd.isBefore(today) ? naturalEnd : today;

        // Previous semester (immediately before the current one's start date)
        LocalDate prevFrom = null;
        LocalDate prevTo   = null;
        Optional<Semester> prevSem = semesterRepository
                .findFirstByStartDateLessThanOrderByStartDateDesc(sem.getStartDate());
        if (prevSem.isPresent()) {
            Semester prev        = prevSem.get();
            prevFrom             = prev.getStartDate();
            LocalDate prevNaturalEnd = prev.getEndDate();
            prevTo               = computePrevTo(from, to, naturalEnd, prevFrom, prevNaturalEnd);
        }

        return Optional.of(new ResolvedPeriod(
                StatisticsScope.SEMESTER,
                from, to,
                prevFrom, prevTo,
                buildMonthScaffold(from, naturalEnd)
        ));
    }

    /**
     * Resolves the target semester from an optional anchor UUID string.
     *
     * <p>Default resolution order when no anchor is provided:
     * <ol>
     *   <li>{@link SemesterRepository#findCurrent(LocalDate)} — semester whose range contains today.</li>
     *   <li>{@link SemesterRepository#findTopByOrderByEndDateDesc()} — latest semester by end date
     *       (fallback when no semester is currently active, e.g., between semesters).</li>
     *   <li>{@code null} — no semesters in the database at all.</li>
     * </ol>
     * </p>
     *
     * @param anchor semester UUID string, or {@code null}/{@code blank} for default resolution
     * @param today  reference date for current-semester lookup
     * @return the matching {@link Semester}, or {@code null} if no semesters exist
     * @throws IllegalArgumentException if the anchor is present but not a valid UUID
     * @throws IllegalArgumentException if the UUID is valid but does not match any semester
     */
    private Semester resolveSemester(String anchor, LocalDate today) {
        if (anchor == null || anchor.isBlank()) {
            return semesterRepository.findCurrent(today)
                    .or(semesterRepository::findTopByOrderByEndDateDesc)
                    .orElse(null);
        }

        UUID uuid;
        try {
            uuid = UUID.fromString(anchor.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid anchor for scope SEMESTER");
        }

        return semesterRepository.findByUuid(uuid)
                .orElseThrow(() ->
                        new IllegalArgumentException("Semester not found: " + anchor.trim()));
    }

    /**
     * Builds the complete ordered list of month labels for a semester scaffold.
     *
     * <p>Iterates from the first month of the semester to the last, including months that lie
     * in the future (these will have {@code reservations = 0} in the trend series). Handles
     * cross-year semesters correctly (e.g., Aug–Jan produces {@code ["Ago","Sep",…,"Ene"]}).</p>
     *
     * <p>A well-formed semester never exceeds 12 months (enforced by
     * {@link mx.unam.icf.aulas.modules.academic.semesters.app.SemesterService} on create/update),
     * so a plain month abbreviation is normally unambiguous. As a defensive measure against
     * pre-existing data that predates that guard, when the range spans <em>more than</em> 12
     * months each label is disambiguated with a 2-digit year ({@code "Ene 26"}) — otherwise two
     * dates a year apart would both render as {@code "Ene"} and collide into one bucket in
     * {@link ReservationStatisticsService#buildTrend}.</p>
     *
     * @param semStart first day of the semester (natural start, not capped)
     * @param semEnd   last day of the semester (natural end, not capped by today)
     * @return ordered list of abbreviated month labels in es-MX, year-disambiguated only when
     *         the range exceeds 12 months
     */
    private List<String> buildMonthScaffold(LocalDate semStart, LocalDate semEnd) {
        LocalDate firstMonth = semStart.withDayOfMonth(1);
        long months = ChronoUnit.MONTHS.between(
                YearMonth.from(firstMonth), YearMonth.from(semEnd)) + 1;
        boolean includeYear = months > 12;

        List<String> labels = new ArrayList<>();
        LocalDate cursor = firstMonth;
        while (!cursor.isAfter(semEnd)) {
            labels.add(StatisticsScope.SEMESTER.trendLabel(cursor, includeYear));
            cursor = cursor.plusMonths(1);
        }
        return labels;
    }

    // ── Shared delta-truncation logic ─────────────────────────────────────────

    /**
     * Computes the end date of the previous comparable period, applying proportional truncation
     * only when the current period is still open.
     *
     * <h3>Two cases</h3>
     * <dl>
     *   <dt>Current period in progress ({@code to.isBefore(naturalEnd)})</dt>
     *   <dd>The previous period is truncated to the same number of <em>elapsed days</em> so the
     *       delta compares equal spans: e.g., "1–15 Jun" vs "1–15 May". This is computed as
     *       {@code prevFrom + DAYS.between(from, to)}, capped at {@code prevNaturalEnd}.</dd>
     *
     *   <dt>Current period has closed ({@code to == naturalEnd})</dt>
     *   <dd>The previous period is taken <em>complete</em> ({@code prevTo = prevNaturalEnd}).
     *       Applying day-count arithmetic here would introduce a spurious bias between months of
     *       different lengths (e.g., June has 30 days; May has 31 — subtracting one would leave
     *       out the 31st of May for no reason).</dd>
     * </dl>
     *
     * @param from           first date of the current period
     * @param to             last date of the current period (already capped by today)
     * @param naturalEnd     natural last date of the current period (month-end or semester-end)
     * @param prevFrom       first date of the previous period
     * @param prevNaturalEnd natural last date of the previous period
     * @return the computed end date for the previous period
     */
    private LocalDate computePrevTo(LocalDate from, LocalDate to, LocalDate naturalEnd,
                                    LocalDate prevFrom, LocalDate prevNaturalEnd) {
        if (to.isBefore(naturalEnd)) {
            // Current period is still open: truncate previous period proportionally
            long elapsed  = ChronoUnit.DAYS.between(from, to);
            LocalDate candidate = prevFrom.plusDays(elapsed);
            return candidate.isBefore(prevNaturalEnd) ? candidate : prevNaturalEnd;
        } else {
            // Current period has closed: take the previous period in full
            return prevNaturalEnd;
        }
    }
}
