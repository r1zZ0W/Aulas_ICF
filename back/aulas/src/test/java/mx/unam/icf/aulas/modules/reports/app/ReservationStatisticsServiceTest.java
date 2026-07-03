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
                StatisticsScope.MENSUAL,
                LocalDate.of(2025, 3, 1),
                LocalDate.of(2025, 3, 31),
                LocalDate.of(2025, 2, 1),
                LocalDate.of(2025, 2, 28),
                buildDayLabels(31)
        );
        // lenient: not every test reaches the slot-hours conversion path (short-circuit, invalid-scope)
        lenient().when(slotProps.slotDurationHours()).thenReturn(0.5);
    }

    // ── totalReservas ─────────────────────────────────────────────────────────

    @Test
    void getStatistics_returnsTotalReservasFromDb() {
        when(resolver.resolve(StatisticsScope.MENSUAL, null)).thenReturn(Optional.of(march2025));
        stubEmptyCollections();
        when(statsRepo.countActive(
                march2025.from(), march2025.to(), ReservInstanceStatus.ACTIVE))
                .thenReturn(42L);
        when(statsRepo.countActive(
                march2025.prevFrom(), march2025.prevTo(), ReservInstanceStatus.ACTIVE))
                .thenReturn(0L);  // prevCount=0 → delta=null

        ReservationStatisticsDTO dto = service.getStatistics("MENSUAL", null);

        assertThat(dto.totalReservas()).isEqualTo(42L);
    }

    // ── delta ─────────────────────────────────────────────────────────────────

    @Test
    void getStatistics_deltaNull_whenPrevPeriodIsZero() {
        when(resolver.resolve(StatisticsScope.MENSUAL, null)).thenReturn(Optional.of(march2025));
        stubEmptyCollections();
        when(statsRepo.countActive(march2025.from(), march2025.to(), ReservInstanceStatus.ACTIVE))
                .thenReturn(50L);
        when(statsRepo.countActive(march2025.prevFrom(), march2025.prevTo(), ReservInstanceStatus.ACTIVE))
                .thenReturn(0L);

        assertThat(service.getStatistics("MENSUAL", null).totalReservasDeltaPct()).isNull();
    }

    @Test
    void getStatistics_deltaNull_whenNoPrevPeriod() {
        ResolvedPeriod noPrev = new ResolvedPeriod(
                StatisticsScope.SEMESTRAL,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30),
                null, null,
                List.of("Ene", "Feb", "Mar", "Abr", "May", "Jun")
        );
        when(resolver.resolve(StatisticsScope.SEMESTRAL, null)).thenReturn(Optional.of(noPrev));
        stubEmptyCollections();
        when(statsRepo.countActive(any(), any(), any())).thenReturn(100L);

        assertThat(service.getStatistics("SEMESTRAL", null).totalReservasDeltaPct()).isNull();
        // countActive called only once (no prev period → no second call)
        verify(statsRepo, times(1)).countActive(any(), any(), any());
    }

    @Test
    void getStatistics_deltaPositive_whenActualExceedsPrev() {
        when(resolver.resolve(StatisticsScope.MENSUAL, null)).thenReturn(Optional.of(march2025));
        stubEmptyCollections();
        when(statsRepo.countActive(march2025.from(), march2025.to(), ReservInstanceStatus.ACTIVE))
                .thenReturn(110L);
        when(statsRepo.countActive(march2025.prevFrom(), march2025.prevTo(), ReservInstanceStatus.ACTIVE))
                .thenReturn(100L);

        Double delta = service.getStatistics("MENSUAL", null).totalReservasDeltaPct();

        assertThat(delta).isEqualTo(10.0);  // (110-100)/100 * 100 = 10%
    }

    // ── aulaMasOcupada ────────────────────────────────────────────────────────

    @Test
    void getStatistics_aulaMasOcupada_isFirstElement() {
        when(resolver.resolve(StatisticsScope.MENSUAL, null)).thenReturn(Optional.of(march2025));
        stubForTotal(20L);
        stubEmptyUsers();
        stubEmptyTendencia();

        ClassroomSlotsView aula1 = mockAula("Aula 101", 40L);
        ClassroomSlotsView aula2 = mockAula("Lab Física", 30L);
        when(statsRepo.topClassroomsBySlots(any(), any(), any(), any(Limit.class)))
                .thenReturn(List.of(aula1, aula2));

        ReservationStatisticsDTO dto = service.getStatistics("MENSUAL", null);

        assertThat(dto.aulaMasOcupada()).isNotNull();
        assertThat(dto.aulaMasOcupada().nombre()).isEqualTo("Aula 101");
        // Slot conversion: 40 slots × 0.5 h = 20 h
        assertThat(dto.aulaMasOcupada().horas()).isEqualTo(20.0);
        assertThat(dto.aulasMasOcupadas()).hasSize(2);
    }

    @Test
    void getStatistics_aulaMasOcupada_isNull_whenNoData() {
        when(resolver.resolve(StatisticsScope.MENSUAL, null)).thenReturn(Optional.of(march2025));
        stubForTotal(0L);
        when(statsRepo.topClassroomsBySlots(any(), any(), any(), any(Limit.class)))
                .thenReturn(List.of());
        stubEmptyUsers();
        stubEmptyTendencia();

        assertThat(service.getStatistics("MENSUAL", null).aulaMasOcupada()).isNull();
    }

    // ── slot → horas uses slotProps, not a hardcoded 0.5 ─────────────────────

    @Test
    void getStatistics_horasCalculatedFromSlotProps_not_hardcoded() {
        // Configure 45-minute slots (not the default 30)
        when(slotProps.slotDurationHours()).thenReturn(0.75);
        when(resolver.resolve(StatisticsScope.MENSUAL, null)).thenReturn(Optional.of(march2025));
        stubForTotal(10L);
        stubEmptyUsers();
        stubEmptyTendencia();

        ClassroomSlotsView aula = mockAula("Sala A", 4L);
        when(statsRepo.topClassroomsBySlots(any(), any(), any(), any(Limit.class)))
                .thenReturn(List.of(aula));

        ReservationStatisticsDTO dto = service.getStatistics("MENSUAL", null);

        // 4 slots × 0.75 h = 3.0 h (not 4 × 0.5 = 2.0)
        assertThat(dto.aulaMasOcupada().horas()).isEqualTo(3.0);
    }

    // ── recurrencia ───────────────────────────────────────────────────────────
    //
    // NOTE: recurrence is now counted by reservation (group), not by session-day.
    // countInstancesPerGroup returns one entry per distinct group with instances in the period,
    // holding that group's instance count within [from, to]; the service classifies each entry
    // (>1 = recurrente, ==1 = eventual). These service-level tests verify that classification and
    // the tasa/eventuales arithmetic — the query itself is covered by
    // ReportStatisticsRepository's javadoc/SQL.

    @Test
    void getStatistics_recurrencia_countsGroups_notInstances() {
        when(resolver.resolve(StatisticsScope.MENSUAL, null)).thenReturn(Optional.of(march2025));
        stubEmptyAulas();
        stubEmptyUsers();
        stubEmptyTendencia();
        when(statsRepo.countActive(march2025.from(), march2025.to(), ReservInstanceStatus.ACTIVE))
                .thenReturn(20L);  // e.g. a 12-session weekly class + 8 one-off bookings
        when(statsRepo.countActive(march2025.prevFrom(), march2025.prevTo(), ReservInstanceStatus.ACTIVE))
                .thenReturn(0L);
        // 5 distinct groups: one recurring (12 sessions in the period), four eventual (1 each)
        when(statsRepo.countInstancesPerGroup(march2025.from(), march2025.to(), ReservInstanceStatus.ACTIVE))
                .thenReturn(List.of(12L, 1L, 1L, 1L, 1L));

        ReservationStatisticsDTO dto = service.getStatistics("MENSUAL", null);

        assertThat(dto.recurrencia().recurrentes()).isEqualTo(1L);   // one recurring group
        assertThat(dto.recurrencia().eventuales()).isEqualTo(4L);    // four eventual groups
        // Invariant is now over distinct groups, NOT totalReservas (which counts session-days)
        assertThat(dto.recurrencia().recurrentes() + dto.recurrencia().eventuales())
                .isEqualTo(5L)
                .isNotEqualTo(dto.totalReservas());
    }

    @Test
    void getStatistics_tasaRecurrencia_computedOverGroupCount() {
        when(resolver.resolve(StatisticsScope.MENSUAL, null)).thenReturn(Optional.of(march2025));
        stubEmptyAulas();
        stubEmptyUsers();
        stubEmptyTendencia();
        when(statsRepo.countActive(any(), any(), eq(ReservInstanceStatus.ACTIVE)))
                .thenReturn(5L).thenReturn(0L);
        // 5 groups, 2 recurring (>1 instance), 3 eventual → 2/5 = 40%
        when(statsRepo.countInstancesPerGroup(any(), any(), any()))
                .thenReturn(List.of(3L, 1L, 1L, 5L, 1L));

        assertThat(service.getStatistics("MENSUAL", null).tasaRecurrenciaPct()).isEqualTo(40.0);
    }

    @Test
    void getStatistics_tasaRecurrencia_zeroWhenNoReservas() {
        when(resolver.resolve(StatisticsScope.MENSUAL, null)).thenReturn(Optional.of(march2025));
        stubEmptyAulas();
        stubEmptyUsers();
        stubEmptyTendencia();
        when(statsRepo.countActive(any(), any(), any())).thenReturn(0L);
        when(statsRepo.countInstancesPerGroup(any(), any(), any())).thenReturn(List.of());

        assertThat(service.getStatistics("MENSUAL", null).tasaRecurrenciaPct()).isEqualTo(0.0);
    }

    // ── nombre sanitizado (sin espacio fantasma) ──────────────────────────────

    @Test
    void getStatistics_usuarioNombreIsTrimmmed_whenLastNameAbsent() {
        when(resolver.resolve(StatisticsScope.MENSUAL, null)).thenReturn(Optional.of(march2025));
        stubForTotal(1L);
        stubEmptyAulas();
        stubEmptyTendencia();

        // The DB returns "Daniel " (with trailing space from CONCAT without TRIM) —
        // but our query uses TRIM, so the projection receives "Daniel".
        // We simulate the projection already having the clean value.
        UserReservationsView u = mockUser("Daniel", 1L);
        when(statsRepo.topUsersByReservations(any(), any(), any(), any(Limit.class)))
                .thenReturn(List.of(u));

        ReservationStatisticsDTO dto = service.getStatistics("MENSUAL", null);

        assertThat(dto.mayorUsuario()).isNotNull();
        assertThat(dto.mayorUsuario().nombre()).isEqualTo("Daniel");
        assertThat(dto.mayorUsuario().nombre()).doesNotEndWith(" ");
    }

    // ── tendencia MENSUAL ─────────────────────────────────────────────────────

    @Test
    void getStatistics_tendencia_fillsZerosForDaysWithNoData() {
        when(resolver.resolve(StatisticsScope.MENSUAL, null)).thenReturn(Optional.of(march2025));
        stubForTotal(2L);
        stubEmptyAulas();
        stubEmptyUsers();

        // Only day 15 has data
        DateCountView row = mockDateCount(LocalDate.of(2025, 3, 15), 2L);
        when(statsRepo.countPerDate(any(), any(), any())).thenReturn(List.of(row));

        ReservationStatisticsDTO dto = service.getStatistics("MENSUAL", null);

        assertThat(dto.tendencia()).hasSize(31);  // March has 31 days
        assertThat(dto.tendencia().get(14).label()).isEqualTo("15");  // index 14 = day 15
        assertThat(dto.tendencia().get(14).reservas()).isEqualTo(2L);
        assertThat(dto.tendencia().get(0).reservas()).isEqualTo(0L);   // day 1 = 0
        assertThat(dto.tendencia().get(30).reservas()).isEqualTo(0L);  // day 31 = 0
    }

    // ── tendencia SEMESTRAL ───────────────────────────────────────────────────

    @Test
    void getStatistics_tendencia_semestral_aggregatesByMonth() {
        ResolvedPeriod sem = new ResolvedPeriod(
                StatisticsScope.SEMESTRAL,
                LocalDate.of(2026, 1, 12), LocalDate.of(2026, 6, 26),
                null, null,
                List.of("Ene", "Feb", "Mar", "Abr", "May", "Jun")
        );
        when(resolver.resolve(StatisticsScope.SEMESTRAL, null)).thenReturn(Optional.of(sem));
        stubForTotal(10L);
        stubEmptyAulas();
        stubEmptyUsers();

        // Two days in January, one in March.
        // Mocks must be created before the outer when() call, because mockDateCount()
        // internally calls when(v.getX())... which would interrupt an open Mockito stub.
        DateCountView jan15 = mockDateCount(LocalDate.of(2026, 1, 15), 3L);
        DateCountView jan20 = mockDateCount(LocalDate.of(2026, 1, 20), 2L);
        DateCountView mar5  = mockDateCount(LocalDate.of(2026, 3, 5),  5L);
        when(statsRepo.countPerDate(any(), any(), any()))
                .thenReturn(List.of(jan15, jan20, mar5));

        ReservationStatisticsDTO dto = service.getStatistics("SEMESTRAL", null);

        assertThat(dto.tendencia()).hasSize(6);
        assertThat(dto.tendencia().get(0).label()).isEqualTo("Ene");
        assertThat(dto.tendencia().get(0).reservas()).isEqualTo(5L);  // 3 + 2
        assertThat(dto.tendencia().get(2).label()).isEqualTo("Mar");
        assertThat(dto.tendencia().get(2).reservas()).isEqualTo(5L);
        assertThat(dto.tendencia().get(1).reservas()).isEqualTo(0L);  // Feb = 0
    }

    @Test
    void getStatistics_tendencia_semestral_disambiguatesRepeatedMonthsAcrossYears() {
        // A malformed multi-year "semester" (>12 months) — the scaffold must include the year
        // in each label, and dates a year apart must NOT collapse into the same bucket.
        List<String> scaffold = List.of(
                "Ene 25", "Feb 25", "Mar 25", "Abr 25", "May 25", "Jun 25",
                "Jul 25", "Ago 25", "Sep 25", "Oct 25", "Nov 25", "Dic 25",
                "Ene 26");
        ResolvedPeriod multiYear = new ResolvedPeriod(
                StatisticsScope.SEMESTRAL,
                LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 31),
                null, null,
                scaffold);
        when(resolver.resolve(StatisticsScope.SEMESTRAL, null)).thenReturn(Optional.of(multiYear));
        stubForTotal(10L);
        stubEmptyAulas();
        stubEmptyUsers();

        DateCountView jan2025 = mockDateCount(LocalDate.of(2025, 1, 10), 4L);
        DateCountView jan2026 = mockDateCount(LocalDate.of(2026, 1, 10), 7L);
        when(statsRepo.countPerDate(any(), any(), any()))
                .thenReturn(List.of(jan2025, jan2026));

        ReservationStatisticsDTO dto = service.getStatistics("SEMESTRAL", null);

        assertThat(dto.tendencia()).hasSize(13);
        assertThat(dto.tendencia().get(0).label()).isEqualTo("Ene 25");
        assertThat(dto.tendencia().get(0).reservas()).isEqualTo(4L);   // NOT summed with Jan 2026
        assertThat(dto.tendencia().get(12).label()).isEqualTo("Ene 26");
        assertThat(dto.tendencia().get(12).reservas()).isEqualTo(7L);  // kept in its own bucket
    }

    // ── short-circuit — rango vacío ───────────────────────────────────────────

    @Test
    void getStatistics_shortCircuit_whenResolverReturnsEmpty() {
        when(resolver.resolve(StatisticsScope.SEMESTRAL, null)).thenReturn(Optional.empty());

        ReservationStatisticsDTO dto = service.getStatistics("SEMESTRAL", null);

        // No database calls at all
        verifyNoInteractions(statsRepo);

        // DTO is in safe zeroed state
        assertThat(dto.totalReservas()).isEqualTo(0L);
        assertThat(dto.totalReservasDeltaPct()).isNull();
        assertThat(dto.aulaMasOcupada()).isNull();
        assertThat(dto.mayorUsuario()).isNull();
        assertThat(dto.tasaRecurrenciaPct()).isEqualTo(0.0);
        assertThat(dto.aulasMasOcupadas()).isEmpty();
        assertThat(dto.usuariosMasReservas()).isEmpty();
    }

    @Test
    void getStatistics_shortCircuit_tendenciaNotEmpty() {
        when(resolver.resolve(StatisticsScope.SEMESTRAL, null)).thenReturn(Optional.empty());

        ReservationStatisticsDTO dto = service.getStatistics("SEMESTRAL", null);

        // Trend scaffold must not be empty so chart axes don't collapse
        assertThat(dto.tendencia()).isNotEmpty();
        assertThat(dto.tendencia()).allMatch(item -> item.reservas() == 0L);
    }

    @Test
    void getStatistics_shortCircuit_mensualTendenciaHasCurrentMonthDays() {
        when(resolver.resolve(StatisticsScope.MENSUAL, null)).thenReturn(Optional.empty());

        ReservationStatisticsDTO dto = service.getStatistics("MENSUAL", null);

        int expectedDays = YearMonth.now().lengthOfMonth();
        assertThat(dto.tendencia()).hasSize(expectedDays);
        assertThat(dto.tendencia().get(0).label()).isEqualTo("01");
    }

    // ── scope inválido ────────────────────────────────────────────────────────

    @Test
    void getStatistics_invalidScope_throwsIllegalArgument() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.getStatistics("SEMANAL", null))
                .withMessage("scope debe ser MENSUAL o SEMESTRAL");
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

    private void stubEmptyAulas() {
        when(statsRepo.topClassroomsBySlots(any(), any(), any(), any(Limit.class)))
                .thenReturn(List.of());
    }

    private void stubEmptyUsers() {
        when(statsRepo.topUsersByReservations(any(), any(), any(), any(Limit.class)))
                .thenReturn(List.of());
    }

    private void stubEmptyTendencia() {
        when(statsRepo.countPerDate(any(), any(), any())).thenReturn(List.of());
    }

    private void stubEmptyCollections() {
        when(statsRepo.countActive(any(), any(), any())).thenReturn(0L);
        when(statsRepo.countInstancesPerGroup(any(), any(), any())).thenReturn(List.of());
        stubEmptyAulas();
        stubEmptyUsers();
        stubEmptyTendencia();
    }

    private ClassroomSlotsView mockAula(String nombre, long slots) {
        ClassroomSlotsView v = mock(ClassroomSlotsView.class);
        when(v.getNombre()).thenReturn(nombre);
        when(v.getTotalSlots()).thenReturn(slots);
        return v;
    }

    private UserReservationsView mockUser(String nombre, long reservas) {
        UserReservationsView v = mock(UserReservationsView.class);
        when(v.getNombre()).thenReturn(nombre);
        when(v.getReservas()).thenReturn(reservas);
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
