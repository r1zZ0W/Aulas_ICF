package mx.unam.icf.aulas.modules.reports.app.projections;

import java.time.LocalDate;

/**
 * Spring Data interface projection for per-date reservation-count aggregation.
 *
 * <p>Returned by {@code ReportStatisticsRepository.countPerDate()}, which groups active
 * {@code ReservInstance} records by their date within the queried range. Each instance
 * represents one distinct date on which at least one active reservation exists.</p>
 *
 * <p>The caller ({@link mx.unam.icf.aulas.modules.reports.app.ReservationStatisticsService})
 * fills in zero-reservation days/months by iterating over the full scaffold from
 * {@link mx.unam.icf.aulas.modules.reports.app.ResolvedPeriod#trendLabels()} and
 * looking up each label in the map derived from these rows.</p>
 */
public interface DateCountView {

    /**
     * Returns the reservation date for this bucket.
     * One distinct date per row; the caller converts it to a trend label via
     * {@link mx.unam.icf.aulas.modules.reports.app.StatisticsScope#trendLabel(LocalDate)}.
     *
     * @return non-null reservation date
     */
    LocalDate getDate();

    /**
     * Returns the number of active {@code ReservInstance} records on this date.
     *
     * @return count; always {@code >= 1} (dates with zero reservations are not returned by
     *         the query — zero-filling is done in the service layer)
     */
    long getTotal();
}
