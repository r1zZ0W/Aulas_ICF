package mx.unam.icf.aulas.modules.reservations.instances.domain;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins down the exact boundary of {@link ReservationTimeframeRule} — the single place that
 * decides when a reservation stops being "Activa" and becomes "Finalizada".
 *
 * <p>{@link #of} and {@link #toPredicate} must agree: a date classified as {@code PAST} by one
 * must be excluded from the {@code UPCOMING} predicate built by the other, and vice versa —
 * otherwise the badge shown in the UI and the rows returned by the {@code timeframe} filter
 * would disagree for the same reservation.</p>
 */
class ReservationTimeframeRuleTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 31);

    // ── of() — classification ────────────────────────────────────────────────────

    @Test
    void of_returnsPast_whenDateIsYesterday() {
        assertThat(ReservationTimeframeRule.of(TODAY.minusDays(1), TODAY))
                .isEqualTo(ReservationTimeframe.PAST);
    }

    @Test
    void of_returnsUpcoming_whenDateIsToday() {
        // The boundary: "today" belongs to the whole calendar day, not yet PAST.
        assertThat(ReservationTimeframeRule.of(TODAY, TODAY))
                .isEqualTo(ReservationTimeframe.UPCOMING);
    }

    @Test
    void of_returnsUpcoming_whenDateIsTomorrow() {
        assertThat(ReservationTimeframeRule.of(TODAY.plusDays(1), TODAY))
                .isEqualTo(ReservationTimeframe.UPCOMING);
    }

    // ── toPredicate() — Criteria wiring ──────────────────────────────────────────
    // Isolated in a @Nested + @ExtendWith(MockitoExtension.class) class so the plain-JUnit
    // classification tests above (of()) need no mocking framework at all.

    @Nested
    @ExtendWith(MockitoExtension.class)
    class PredicateWiring {

        @Mock private CriteriaBuilder cb;
        @Mock private Expression<LocalDate> datePath;
        @Mock private Predicate result;

        @Test
        void past_delegatesToLessThan() {
            when(cb.lessThan(datePath, TODAY)).thenReturn(result);

            Predicate actual = ReservationTimeframeRule.toPredicate(cb, datePath, ReservationTimeframe.PAST, TODAY);

            assertThat(actual).isSameAs(result);
            verify(cb).lessThan(datePath, TODAY);
            verifyNoMoreInteractions(cb);
        }

        @Test
        void upcoming_delegatesToGreaterThanOrEqualTo() {
            when(cb.greaterThanOrEqualTo(datePath, TODAY)).thenReturn(result);

            Predicate actual = ReservationTimeframeRule.toPredicate(cb, datePath, ReservationTimeframe.UPCOMING, TODAY);

            assertThat(actual).isSameAs(result);
            verify(cb).greaterThanOrEqualTo(datePath, TODAY);
            verifyNoMoreInteractions(cb);
        }
    }
}
