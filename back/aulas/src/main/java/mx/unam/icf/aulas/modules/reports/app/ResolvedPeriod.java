package mx.unam.icf.aulas.modules.reports.app;

import java.time.LocalDate;
import java.util.List;

/**
 * Immutable value object holding the fully-resolved date ranges and trend scaffold for one
 * statistics query, as computed by {@link StatisticsPeriodResolver}.
 *
 * <p>Consumers ({@link ReservationStatisticsService}) should treat this as a read-only
 * specification: all validation and date arithmetic is centralised in the resolver, so the
 * service only needs to pass {@link #from()}/{@link #to()} to repository queries.</p>
 *
 * <h3>Delta truncation contract</h3>
 * <p>{@link #prevTo()} is computed differently depending on whether the current period has
 * already closed:</p>
 * <ul>
 *   <li><b>In progress</b> ({@code to < natural end}): {@code prevTo} is truncated to the same
 *       number of elapsed days, making the comparison fair (e.g., "1–15 Jun" vs "1–15 May").</li>
 *   <li><b>Closed</b> ({@code to == natural end}): {@code prevTo} is the natural end of the
 *       previous period (taken complete), avoiding a day-count bias between months of different
 *       lengths.</li>
 * </ul>
 *
 * @param scope       the granularity used to build this period
 * @param from        first date of the current analysis period (inclusive)
 * @param to          last date of the current analysis period (inclusive);
 *                    always {@code <= LocalDate.now()} — future dates are never included
 * @param prevFrom    first date of the comparable previous period (inclusive),
 *                    or {@code null} when no previous period exists
 * @param prevTo      last date of the comparable previous period (inclusive, see truncation
 *                    contract above), or {@code null} when no previous period exists
 * @param trendLabels ordered, complete scaffold of trend-series labels for the full period
 *                    (including future days/months with zero reservations);
 *                    never empty
 */
public record ResolvedPeriod(
        StatisticsScope scope,
        LocalDate from,
        LocalDate to,
        LocalDate prevFrom,
        LocalDate prevTo,
        List<String> trendLabels
) {}
