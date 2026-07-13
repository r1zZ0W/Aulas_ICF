package mx.unam.icf.aulas.modules.reports.app;

import mx.unam.icf.aulas.modules.reports.app.dtos.ReservationStatisticsDTO;
import mx.unam.icf.aulas.modules.reports.app.projections.ClassroomSlotsView;
import mx.unam.icf.aulas.modules.reports.app.projections.DateCountView;
import mx.unam.icf.aulas.modules.reports.app.projections.UserReservationsView;
import mx.unam.icf.aulas.modules.reports.infrastructure.ReportStatisticsRepository;
import mx.unam.icf.aulas.modules.reservations.instances.domain.ReservInstanceStatus;
import mx.unam.icf.aulas.modules.reservations.slots.app.ReservationSlotProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ReservationStatisticsService}.
 *
 * <p>All repository calls and collaborators are mocked; this test class exercises only
 * the orchestration logic in the service (delta computation, recurrence arithmetic,
 * trend scaffold merging, short-circuit path).</p>
 */
@ExtendWith(MockitoExtension.class)
class ReservationStatisticsServiceTest {

    @Mock private StatisticsPeriodResolver    resolver;
    @Mock private ReportStatisticsRepository  statsRepo;
    @Mock private ReservationSlotProperties   slotProps;

    @InjectMocks
    private ReservationStatisticsService service;

    /** A resolved period that covers March 2025 (closed month — entirely in the past). */
    private ResolvedPeriod march2025;

    @BeforeEach
    void setUp() {
        // Closed month: 1-Mar to 31-Mar 2025; previous = Feb 2025 (complete)
        march2025 = new ResolvedPeriod(
                StatisticsScope.MONTHLY,
                LocalDate.of(2025, 3, 1),
                LocalDate.of(2025, 3, 31),
                LocalDate.of(2025, 2, 1),
                LocalDate.of(2025, 2, 28),
                buildDayLabels(31)
        );
        // lenient: not every test reaches the slot-hours conversion path (short-circuit, invalid-scope)
        lenient().when(slotProps.slotDurationHours()).thenReturn(0.5);
    }

    // ── totalReservations ─────────────────────────────────────────────────────

    @Test
    void getStatistics_returnsTotalReservationsFromDb() {
        when(resolver.resolve(StatisticsScope.MONTHLY, null)).thenReturn(Optional.of(march2025));
        stubEmptyCollections();
        when(statsRepo.countActive(
                march2025.from(), march2025.to(), ReservInstanceStatus.ACTIVE))
                .thenReturn(42L);
        when(statsRepo.countActive(
                march2025.prevFrom(), march2025.prevTo(), ReservInstanceStatus.ACTIVE))
                .thenReturn(0L);  // prevCount=0 → delta=null

        ReservationStatisticsDTO dto = service.getStatistics("MONTHLY", null);

        assertThat(dto.totalReservations()).isEqualTo(42L);
    }

    // ── delta ─────────────────────────────────────────────────────────────────

    @Test
    void getStatistics_deltaNull_whenPrevPeriodIsZero() {
        when(resolver.resolve(StatisticsScope.MONTHLY, null)).thenReturn(Optional.of(march2025));
        stubEmptyCollections();
        when(statsRepo.countActive(march2025.from(), march2025.to(), ReservInstanceStatus.ACTIVE))
                .thenReturn(50L);
        when(statsRepo.countActive(march2025.prevFrom(), march2025.prevTo(), ReservInstanceStatus.ACTIVE))
                .thenReturn(0L);

        assertThat(service.getStatistics("MONTHLY", null).totalReservationsDeltaPct()).isNull();
    }

    @Test
    void getStatistics_deltaNull_whenNoPrevPeriod() {
        ResolvedPeriod noPrev = new ResolvedPeriod(
                StatisticsScope.SEMESTER,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30),
                null, null,
                List.of("Ene", "Feb", "Mar", "Abr", "May", "Jun")
        );
        when(resolver.resolve(StatisticsScope.SEMESTER, null)).thenReturn(Optional.of(noPrev));
        stubEmptyCollections();
        when(statsRepo.countActive(any(), any(), any())).thenReturn(100L);

        assertThat(service.getStatistics("SEMESTER", null).totalReservationsDeltaPct()).isNull();
        // countActive called only once (no prev period → no second call)
        verify(statsRepo, times(1)).countActive(any(), any(), any());
    }

    @Test
    void getStatistics_deltaPositive_whenActualExceedsPrev() {
        when(resolver.resolve(StatisticsScope.MONTHLY, null)).thenReturn(Optional.of(march2025));
        stubEmptyCollections();
        when(statsRepo.countActive(march2025.from(), march2025.to(), ReservInstanceStatus.ACTIVE))
                .thenReturn(110L);
        when(statsRepo.countActive(march2025.prevFrom(), march2025.prevTo(), ReservInstanceStatus.ACTIVE))
                .thenReturn(100L);

        Double delta = service.getStatistics("MONTHLY", null).totalReservationsDeltaPct();

        assertThat(delta).isEqualTo(10.0);  // (110-100)/100 * 100 = 10%
    }

    // ── mostOccupiedClassroom ─────────────────────────────────────────────────

    @Test
    void getStatistics_mostOccupiedClassroom_isFirstElement() {
        when(resolver.resolve(StatisticsScope.MONTHLY, null)).thenReturn(Optional.of(march2025));
        stubForTotal(20L);
        stubEmptyUsers();
        stubEmptyTrend();

        ClassroomSlotsView room1 = mockClassroom("Aula 101", 40L);
        ClassroomSlotsView room2 = mockClassroom("Lab Física", 30L);
        when(statsRepo.topClassroomsBySlots(any(), any(), any(), any(Limit.class)))
                .thenReturn(List.of(room1, room2));

        ReservationStatisticsDTO dto = service.getStatistics("MONTHLY", null);

        assertThat(dto.mostOccupiedClassroom()).isNotNull();
        assertThat(dto.mostOccupiedClassroom().name()).isEqualTo("Aula 101");
        // Slot conversion: 40 slots × 0.5 h = 20 h
        assertThat(dto.mostOccupiedClassroom().hours()).isEqualTo(20.0);
        assertThat(dto.mostOccupiedClassrooms()).hasSize(2);
    }

    @Test
    void getStatistics_mostOccupiedClassroom_isNull_whenNoData() {
        when(resolver.resolve(StatisticsScope.MONTHLY, null)).thenReturn(Optional.of(march2025));
        stubForTotal(0L);
        when(statsRepo.topClassroomsBySlots(any(), any(), any(), any(Limit.class)))
                .thenReturn(List.of());
        stubEmptyUsers();
        stubEmptyTrend();

        assertThat(service.getStatistics("MONTHLY", null).mostOccupiedClassroom()).isNull();
    }

    // ── slot → hours uses slotProps, not a hardcoded 0.5 ─────────────────────

    @Test
    void getStatistics_hoursCalculatedFromSlotProps_not_hardcoded() {
        // Configure 45-minute slots (not the default 30)
        when(slotProps.slotDurationHours()).thenReturn(0.75);
        when(resolver.resolve(StatisticsScope.MONTHLY, null)).thenReturn(Optional.of(march2025));
        stubForTotal(10L);
        stubEmptyUsers();
        stubEmptyTrend();

        ClassroomSlotsView room = mockClassroom("Sala A", 4L);
        when(statsRepo.topClassroomsBySlots(any(), any(), any(), any(Limit.class)))
                .thenReturn(List.of(room));

        ReservationStatisticsDTO dto = service.getStatistics("MONTHLY", null);

        // 4 slots × 0.75 h = 3.0 h (not 4 × 0.5 = 2.0)
        assertThat(dto.mostOccupiedClassroom().hours()).isEqualTo(3.0);
    }

    // ── recurrence ────────────────────────────────────────────────────────────
    //
    // NOTE: recurrence is counted by reservation (group), not by session-day.
    // countInstancesPerGroup returns one entry per distinct group with instances in the period,
    // holding that group's instance count within [from, to]; the service classifies each entry
    // (>1 = recurring, ==1 = one-time). These service-level tests verify that classification and
    // the rate/oneTime arithmetic — the query itself is covered by
    // ReportStatisticsRepository's javadoc/SQL.

    @Test
    void getStatistics_recurrence_countsGroups_notInstances() {
        when(resolver.resolve(StatisticsScope.MONTHLY, null)).thenReturn(Optional.of(march2025));
        stubEmptyClassrooms();
        stubEmptyUsers();
        stubEmptyTrend();
        when(statsRepo.countActive(march2025.from(), march2025.to(), ReservInstanceStatus.ACTIVE))
                .thenReturn(20L);  // e.g. a 12-session weekly class + 8 one-off bookings
        when(statsRepo.countActive(march2025.prevFrom(), march2025.prevTo(), ReservInstanceStatus.ACTIVE))
                .thenReturn(0L);
        // 5 distinct groups: one recurring (12 sessions in the period), four one-time (1 each)
        when(statsRepo.countInstancesPerGroup(march2025.from(), march2025.to(), ReservInstanceStatus.ACTIVE))
                .thenReturn(List.of(12L, 1L, 1L, 1L, 1L));

        ReservationStatisticsDTO dto = service.getStatistics("MONTHLY", null);

        assertThat(dto.recurrence().recurring()).isEqualTo(1L);   // one recurring group
        assertThat(dto.recurrence().oneTime()).isEqualTo(4L);     // four one-time groups
        // Invariant is over distinct groups, NOT totalReservations (which counts session-days)
        assertThat(dto.recurrence().recurring() + dto.recurrence().oneTime())
                .isEqualTo(5L)
                .isNotEqualTo(dto.totalReservations());
    }

    @Test
    void getStatistics_recurrenceRate_computedOverGroupCount() {
        when(resolver.resolve(StatisticsScope.MONTHLY, null)).thenReturn(Optional.of(march2025));
        stubEmptyClassrooms();
        stubEmptyUsers();
        stubEmptyTrend();
        when(statsRepo.countActive(any(), any(), eq(ReservInstanceStatus.ACTIVE)))
                .thenReturn(5L).thenReturn(0L);
        // 5 groups, 2 recurring (>1 instance), 3 one-time → 2/5 = 40%
        when(statsRepo.countInstancesPerGroup(any(), any(), any()))
                .thenReturn(List.of(3L, 1L, 1L, 5L, 1L));

        assertThat(service.getStatistics("MONTHLY", null).recurrenceRatePct()).isEqualTo(40.0);
    }

    @Test
    void getStatistics_recurrenceRate_zeroWhenNoReservations() {
        when(resolver.resolve(StatisticsScope.MONTHLY, null)).thenReturn(Optional.of(march2025));
        stubEmptyClassrooms();
        stubEmptyUsers();
        stubEmptyTrend();
        when(statsRepo.countActive(any(), any(), any())).thenReturn(0L);
        when(statsRepo.countInstancesPerGroup(any(), any(), any())).thenReturn(List.of());

        assertThat(service.getStatistics("MONTHLY", null).recurrenceRatePct()).isEqualTo(0.0);
    }

    // ── sanitized name (no phantom trailing space) ────────────────────────────

    @Test
    void getStatistics_userNameIsTrimmed_whenLastNameAbsent() {
        when(resolver.resolve(StatisticsScope.MONTHLY, null)).thenReturn(Optional.of(march2025));
        stubForTotal(1L);
        stubEmptyClassrooms();
        stubEmptyTrend();

        // The DB would return "Daniel " (trailing space from CONCAT without TRIM) —
        // but the query uses TRIM, so the projection receives "Daniel".
        // We simulate the projection already having the clean value.
        UserReservationsView u = mockUser("Daniel", 1L);
        when(statsRepo.topUsersByReservations(any(), any(), any(), any(Limit.class)))
                .thenReturn(List.of(u));

        ReservationStatisticsDTO dto = service.getStatistics("MONTHLY", null);

        assertThat(dto.topUser()).isNotNull();
        assertThat(dto.topUser().name()).isEqualTo("Daniel");
        assertThat(dto.topUser().name()).doesNotEndWith(" ");
    }

    // ── trend MONTHLY ─────────────────────────────────────────────────────────

    @Test
    void getStatistics_trend_fillsZerosForDaysWithNoData() {
        when(resolver.resolve(StatisticsScope.MONTHLY, null)).thenReturn(Optional.of(march2025));
        stubForTotal(2L);
        stubEmptyClassrooms();
        stubEmptyUsers();

        // Only day 15 has data
        DateCountView row = mockDateCount(LocalDate.of(2025, 3, 15), 2L);
        when(statsRepo.countPerDate(any(), any(), any())).thenReturn(List.of(row));

        ReservationStatisticsDTO dto = service.getStatistics("MONTHLY", null);

        assertThat(dto.trend()).hasSize(31);  // March has 31 days
        assertThat(dto.trend().get(14).label()).isEqualTo("15");  // index 14 = day 15
        assertThat(dto.trend().get(14).reservations()).isEqualTo(2L);
        assertThat(dto.trend().get(0).reservations()).isEqualTo(0L);   // day 1 = 0
        assertThat(dto.trend().get(30).reservations()).isEqualTo(0L);  // day 31 = 0
    }

    // ── trend SEMESTER ────────────────────────────────────────────────────────

    @Test
    void getStatistics_trend_semester_aggregatesByMonth() {
        ResolvedPeriod sem = new ResolvedPeriod(
                StatisticsScope.SEMESTER,
                LocalDate.of(2026, 1, 12), LocalDate.of(2026, 6, 26),
                null, null,
                List.of("Ene", "Feb", "Mar", "Abr", "May", "Jun")
        );
        when(resolver.resolve(StatisticsScope.SEMESTER, null)).thenReturn(Optional.of(sem));
        stubForTotal(10L);
        stubEmptyClassrooms();
        stubEmptyUsers();

        // Two days in January, one in March.
        // Mocks must be created before the outer when() call, because mockDateCount()
        // internally calls when(v.getX())... which would interrupt an open Mockito stub.
        DateCountView jan15 = mockDateCount(LocalDate.of(2026, 1, 15), 3L);
        DateCountView jan20 = mockDateCount(LocalDate.of(2026, 1, 20), 2L);
        DateCountView mar5  = mockDateCount(LocalDate.of(2026, 3, 5),  5L);
        when(statsRepo.countPerDate(any(), any(), any()))
                .thenReturn(List.of(jan15, jan20, mar5));

        ReservationStatisticsDTO dto = service.getStatistics("SEMESTER", null);

        assertThat(dto.trend()).hasSize(6);
        assertThat(dto.trend().get(0).label()).isEqualTo("Ene");
        assertThat(dto.trend().get(0).reservations()).isEqualTo(5L);  // 3 + 2
        assertThat(dto.trend().get(2).label()).isEqualTo("Mar");
        assertThat(dto.trend().get(2).reservations()).isEqualTo(5L);
        assertThat(dto.trend().get(1).reservations()).isEqualTo(0L);  // Feb = 0
    }

    @Test
    void getStatistics_trend_semester_disambiguatesRepeatedMonthsAcrossYears() {
        // A malformed multi-year "semester" (>12 months) — the scaffold must include the year
        // in each label, and dates a year apart must NOT collapse into the same bucket.
        List<String> scaffold = List.of(
                "Ene 25", "Feb 25", "Mar 25", "Abr 25", "May 25", "Jun 25",
                "Jul 25", "Ago 25", "Sep 25", "Oct 25", "Nov 25", "Dic 25",
                "Ene 26");
        ResolvedPeriod multiYear = new ResolvedPeriod(
                StatisticsScope.SEMESTER,
                LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 31),
                null, null,
                scaffold);
        when(resolver.resolve(StatisticsScope.SEMESTER, null)).thenReturn(Optional.of(multiYear));
        stubForTotal(10L);
        stubEmptyClassrooms();
        stubEmptyUsers();

        DateCountView jan2025 = mockDateCount(LocalDate.of(2025, 1, 10), 4L);
        DateCountView jan2026 = mockDateCount(LocalDate.of(2026, 1, 10), 7L);
        when(statsRepo.countPerDate(any(), any(), any()))
                .thenReturn(List.of(jan2025, jan2026));

        ReservationStatisticsDTO dto = service.getStatistics("SEMESTER", null);

        assertThat(dto.trend()).hasSize(13);
        assertThat(dto.trend().get(0).label()).isEqualTo("Ene 25");
        assertThat(dto.trend().get(0).reservations()).isEqualTo(4L);   // NOT summed with Jan 2026
        assertThat(dto.trend().get(12).label()).isEqualTo("Ene 26");
        assertThat(dto.trend().get(12).reservations()).isEqualTo(7L);  // kept in its own bucket
    }

    // ── short-circuit — empty range ───────────────────────────────────────────

    @Test
    void getStatistics_shortCircuit_whenResolverReturnsEmpty() {
        when(resolver.resolve(StatisticsScope.SEMESTER, null)).thenReturn(Optional.empty());

        ReservationStatisticsDTO dto = service.getStatistics("SEMESTER", null);

        // No database calls at all
        verifyNoInteractions(statsRepo);

        // DTO is in safe zeroed state
        assertThat(dto.totalReservations()).isEqualTo(0L);
        assertThat(dto.totalReservationsDeltaPct()).isNull();
        assertThat(dto.mostOccupiedClassroom()).isNull();
        assertThat(dto.topUser()).isNull();
        assertThat(dto.recurrenceRatePct()).isEqualTo(0.0);
        assertThat(dto.mostOccupiedClassrooms()).isEmpty();
        assertThat(dto.topUsers()).isEmpty();
    }

    @Test
    void getStatistics_shortCircuit_trendNotEmpty() {
        when(resolver.resolve(StatisticsScope.SEMESTER, null)).thenReturn(Optional.empty());

        ReservationStatisticsDTO dto = service.getStatistics("SEMESTER", null);

        // Trend scaffold must not be empty so chart axes don't collapse
        assertThat(dto.trend()).isNotEmpty();
        assertThat(dto.trend()).allMatch(item -> item.reservations() == 0L);
    }

    @Test
    void getStatistics_shortCircuit_monthlyTrendHasCurrentMonthDays() {
        when(resolver.resolve(StatisticsScope.MONTHLY, null)).thenReturn(Optional.empty());

        ReservationStatisticsDTO dto = service.getStatistics("MONTHLY", null);

        int expectedDays = YearMonth.now().lengthOfMonth();
        assertThat(dto.trend()).hasSize(expectedDays);
        assertThat(dto.trend().get(0).label()).isEqualTo("01");
    }

    // ── invalid scope ─────────────────────────────────────────────────────────

    @Test
    void getStatistics_invalidScope_throwsIllegalArgument() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.getStatistics("WEEKLY", null))
                .withMessage("scope must be MONTHLY or SEMESTER");
        verifyNoInteractions(resolver, statsRepo);
    }

    // ── availableMonths ────────────────────────────────────────────────────────

    @Test
    void availableMonths_formatsYearMonthWithZeroPadding() {
        // MONTH() returns an unpadded int (6, not 06) — the service must zero-pad it.
        when(statsRepo.findActiveYearsAndMonths(ReservInstanceStatus.ACTIVE))
                .thenReturn(List.of(
                        new Object[]{2026, 6},
                        new Object[]{2025, 11}
                ));

        List<String> months = service.availableMonths();

        assertThat(months).containsExactly("2026-06", "2025-11");
    }

    @Test
    void availableMonths_toleratesNonIntegerNumericTypes() {
        // Some JPA providers/databases return Long or Short for YEAR()/MONTH() instead of
        // Integer — the service must read via Number, not cast directly to Integer.
        when(statsRepo.findActiveYearsAndMonths(ReservInstanceStatus.ACTIVE))
                .thenReturn(List.<Object[]>of(new Object[]{2026L, (short) 3}));

        List<String> months = service.availableMonths();

        assertThat(months).containsExactly("2026-03");
    }

    @Test
    void availableMonths_emptyWhenNoActiveReservations() {
        when(statsRepo.findActiveYearsAndMonths(ReservInstanceStatus.ACTIVE))
                .thenReturn(List.of());

        assertThat(service.availableMonths()).isEmpty();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void stubForTotal(long total) {
        when(statsRepo.countActive(any(), any(), any())).thenReturn(total);
        when(statsRepo.countInstancesPerGroup(any(), any(), any())).thenReturn(List.of());
    }

    private void stubEmptyClassrooms() {
        when(statsRepo.topClassroomsBySlots(any(), any(), any(), any(Limit.class)))
                .thenReturn(List.of());
    }

    private void stubEmptyUsers() {
        when(statsRepo.topUsersByReservations(any(), any(), any(), any(Limit.class)))
                .thenReturn(List.of());
    }

    private void stubEmptyTrend() {
        when(statsRepo.countPerDate(any(), any(), any())).thenReturn(List.of());
    }

    private void stubEmptyCollections() {
        when(statsRepo.countActive(any(), any(), any())).thenReturn(0L);
        when(statsRepo.countInstancesPerGroup(any(), any(), any())).thenReturn(List.of());
        stubEmptyClassrooms();
        stubEmptyUsers();
        stubEmptyTrend();
    }

    private ClassroomSlotsView mockClassroom(String name, long slots) {
        ClassroomSlotsView v = mock(ClassroomSlotsView.class);
        when(v.getName()).thenReturn(name);
        when(v.getTotalSlots()).thenReturn(slots);
        return v;
    }

    private UserReservationsView mockUser(String name, long reservations) {
        UserReservationsView v = mock(UserReservationsView.class);
        when(v.getName()).thenReturn(name);
        when(v.getReservations()).thenReturn(reservations);
        return v;
    }

    private DateCountView mockDateCount(LocalDate date, long total) {
        DateCountView v = mock(DateCountView.class);
        when(v.getDate()).thenReturn(date);
        when(v.getTotal()).thenReturn(total);
        return v;
    }

    /** Builds a list of zero-padded day labels for a month with {@code days} days. */
    private List<String> buildDayLabels(int days) {
        return java.util.stream.IntStream.rangeClosed(1, days)
                .mapToObj(d -> String.format("%02d", d))
                .toList();
    }
}
