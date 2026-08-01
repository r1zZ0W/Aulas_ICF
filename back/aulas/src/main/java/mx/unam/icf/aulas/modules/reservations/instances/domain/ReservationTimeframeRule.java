package mx.unam.icf.aulas.modules.reservations.instances.domain;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

import java.time.LocalDate;

/**
 * The single definition of when a reservation stops being current.
 *
 * <h3>The rule</h3>
 * <p>A reservation belongs to <em>Vigentes</em> ({@link ReservationTimeframe#UPCOMING}) for
 * the whole calendar day of its {@code date}, and becomes <em>Finalizada</em>
 * ({@link ReservationTimeframe#PAST}) when the next day starts:</p>
 * <pre>{@code   PAST  ⇔  date < today }</pre>
 *
 * <p>Day precision (rather than the last slot's end time) keeps the predicate a plain
 * comparison on an indexed column — no join to {@code reserv_slots}/{@code time_slots} and no
 * {@code MAX(end_time)} subquery on every listing — and keeps the SQL and the JavaScript
 * saying literally the same thing.</p>
 *
 * <h3>Not to be confused with the cancellation guard</h3>
 * <p>The frontend's {@code hasReservationEnded()} compares against the last slot's end time
 * and answers a different question — "can this still be cancelled?" — which is an
 * <em>operational</em> rule, not a classification. The two thresholds differ on purpose:
 * one decides which bucket a row is displayed in, the other decides which actions are
 * still allowed.</p>
 *
 * <h3>Why both sides live here</h3>
 * <p>{@link #of} classifies a loaded entity and {@link #toPredicate} filters at the database.
 * Keeping them adjacent means a change to the rule (say, moving to slot-level precision)
 * is a single-file edit that cannot leave the badge and the filter disagreeing.</p>
 *
 * @author Ithera
 * @version 1.0
 */
public final class ReservationTimeframeRule {

    private ReservationTimeframeRule() {}

    /**
     * Classifies a reservation date against the reference date.
     *
     * @param date  the reservation's date; must not be {@code null}
     * @param today the reference date, resolved from the application {@code Clock}
     * @return {@link ReservationTimeframe#PAST} when {@code date < today}, otherwise
     *         {@link ReservationTimeframe#UPCOMING}
     */
    public static ReservationTimeframe of(LocalDate date, LocalDate today) {
        return date.isBefore(today) ? ReservationTimeframe.PAST : ReservationTimeframe.UPCOMING;
    }

    /**
     * Builds the Criteria predicate selecting the rows in the given timeframe — the exact
     * database counterpart of {@link #of}.
     *
     * @param cb        the criteria builder
     * @param datePath  path to the instance's {@code date} attribute
     * @param timeframe the timeframe to select; must not be {@code null}
     * @param today     the reference date, resolved from the application {@code Clock}
     * @return {@code date < today} for {@code PAST}, {@code date >= today} for {@code UPCOMING}
     */
    public static Predicate toPredicate(CriteriaBuilder cb,
                                        Expression<LocalDate> datePath,
                                        ReservationTimeframe timeframe,
                                        LocalDate today) {
        return timeframe == ReservationTimeframe.PAST
                ? cb.lessThan(datePath, today)
                : cb.greaterThanOrEqualTo(datePath, today);
    }
}
