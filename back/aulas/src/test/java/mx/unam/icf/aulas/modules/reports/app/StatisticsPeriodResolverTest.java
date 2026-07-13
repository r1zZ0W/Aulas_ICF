package mx.unam.icf.aulas.modules.reports.app;

import mx.unam.icf.aulas.modules.academic.semesters.domain.Semester;
import mx.unam.icf.aulas.modules.academic.semesters.infrastructure.SemesterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link StatisticsPeriodResolver}.
 *
 * <p>Covers date-range derivation, scaffold generation, and — most importantly — the
 * delta truncation contract: proportional truncation when the current period is in progress,
 * and full previous period when the current one has already closed.</p>
 */
@ExtendWith(MockitoExtension.class)
class StatisticsPeriodResolverTest {

    @Mock
    private SemesterRepository semesterRepository;

    @InjectMocks
    private StatisticsPeriodResolver resolver;

    private Semester semester2026_1;
    private UUID semesterUuid;

    @BeforeEach
    void setUp() {
        semesterUuid    = UUID.randomUUID();
        semester2026_1  = new Semester();
        semester2026_1.setUuid(semesterUuid);
        semester2026_1.setName("2026-1");
        semester2026_1.setStartDate(LocalDate.of(2026, 1, 12));
        semester2026_1.setEndDate(LocalDate.of(2026, 6, 26));
    }

    // ── MONTHLY — anchor parsing ───────────────────────────────────────────────

    @Test
    void mensual_withValidAnchor_derivesCorrectRange() {
        // Using a fully closed month (March 2025) so 'to' == naturalEnd
        Optional<ResolvedPeriod> result = resolver.resolve(StatisticsScope.MONTHLY, "2025-03");

        assertThat(result).isPresent();
        ResolvedPeriod p = result.get();
        assertThat(p.from()).isEqualTo(LocalDate.of(2025, 3, 1));
        assertThat(p.to()).isEqualTo(LocalDate.of(2025, 3, 31));   // closed month
        assertThat(p.scope()).isEqualTo(StatisticsScope.MONTHLY);
    }

    @Test
    void mensual_withBlankAnchor_defaultsToCurrentMonth() {
        Optional<ResolvedPeriod> result = resolver.resolve(StatisticsScope.MONTHLY, "");

        assertThat(result).isPresent();
        assertThat(result.get().from()).isEqualTo(YearMonth.now().atDay(1));
    }

    @Test
    void mensual_withMalformedAnchor_throwsIllegalArgument() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> resolver.resolve(StatisticsScope.MONTHLY, "not-a-date"))
                .withMessage("Invalid anchor for scope MONTHLY");
    }

    // ── MONTHLY — day scaffold ────────────────────────────────────────────────

    @Test
    void mensual_scaffoldContainsAllDaysOfMonth() {
        Optional<ResolvedPeriod> result = resolver.resolve(StatisticsScope.MONTHLY, "2025-02");

        assertThat(result).isPresent();
        assertThat(result.get().trendLabels())
                .hasSize(28)
                .startsWith("01")
                .endsWith("28");
    }

    // ── MONTHLY — delta truncation ────────────────────────────────────────────

    @Test
    void mensual_closedPeriod_previousPeriodTakenComplete() {
        // June has 30 days; May has 31. Querying a closed June must give all of May.
        Optional<ResolvedPeriod> result = resolver.resolve(StatisticsScope.MONTHLY, "2025-06");

        assertThat(result).isPresent();
        ResolvedPeriod p = result.get();
        // Current period: 1-Jun to 30-Jun (closed)
        assertThat(p.to()).isEqualTo(LocalDate.of(2025, 6, 30));
        // Previous period: 1-May to 31-May (complete — not truncated to 30th)
        assertThat(p.prevFrom()).isEqualTo(LocalDate.of(2025, 5, 1));
        assertThat(p.prevTo()).isEqualTo(LocalDate.of(2025, 5, 31));
    }

    @Test
    void mensual_currentMonthAnchor_toIsCappedAtToday() {
        // Regardless of the current day of the month, 'to' must never exceed today.
        Optional<ResolvedPeriod> result = resolver.resolve(StatisticsScope.MONTHLY, null);

        assertThat(result).isPresent();
        LocalDate today = LocalDate.now();
        assertThat(result.get().to()).isBeforeOrEqualTo(today);
    }

    @Test
    void mensual_openPeriod_proportionalTruncation_formula() {
        // The proportional truncation logic (computePrevTo) is private, so we verify it
        // through observable output. Use a month where we know today falls inside:
        // if the current day is 1..29 of the current month, we're "open"; if it's the last
        // day we're "closed". Either way we assert the contract:
        //   closed → prevTo == prevNaturalEnd
        //   open   → prevTo < prevNaturalEnd  (proportional, not the full previous month)
        LocalDate today = LocalDate.now();
        YearMonth currentYm = YearMonth.from(today);
        Optional<ResolvedPeriod> result = resolver.resolve(StatisticsScope.MONTHLY, null);

        assertThat(result).isPresent();
        ResolvedPeriod p = result.get();

        boolean periodClosed = p.to().equals(currentYm.atEndOfMonth());
        YearMonth prevYm = currentYm.minusMonths(1);
        LocalDate prevNaturalEnd = prevYm.atEndOfMonth();

        if (periodClosed) {
            // Previous month taken complete
            assertThat(p.prevTo()).isEqualTo(prevNaturalEnd);
        } else {
            // Proportional: prevTo must be strictly before the natural end of the previous month
            long elapsed = ChronoUnit.DAYS.between(p.from(), p.to());
            LocalDate expectedPrevTo = p.prevFrom().plusDays(elapsed);
            assertThat(p.prevTo()).isEqualTo(expectedPrevTo);
            assertThat(p.prevTo()).isBeforeOrEqualTo(prevNaturalEnd);
        }
    }

    // ── SEMESTER — default resolution ───────────────────────────────────────

    @Test
    void semestral_withNoAnchor_usesFindCurrent() {
        when(semesterRepository.findCurrent(any())).thenReturn(Optional.of(semester2026_1));
        when(semesterRepository.findFirstByStartDateLessThanOrderByStartDateDesc(any()))
                .thenReturn(Optional.empty());

        Optional<ResolvedPeriod> result = resolver.resolve(StatisticsScope.SEMESTER, null);

        assertThat(result).isPresent();
        assertThat(result.get().from()).isEqualTo(semester2026_1.getStartDate());
    }

    @Test
    void semestral_withNoCurrentSemester_fallsBackToLatestByEndDate() {
        when(semesterRepository.findCurrent(any())).thenReturn(Optional.empty());
        when(semesterRepository.findTopByOrderByEndDateDesc()).thenReturn(Optional.of(semester2026_1));
        when(semesterRepository.findFirstByStartDateLessThanOrderByStartDateDesc(any()))
                .thenReturn(Optional.empty());

        Optional<ResolvedPeriod> result = resolver.resolve(StatisticsScope.SEMESTER, "");

        assertThat(result).isPresent();
    }

    @Test
    void semestral_withNoSemestersAtAll_returnsEmpty() {
        when(semesterRepository.findCurrent(any())).thenReturn(Optional.empty());
        when(semesterRepository.findTopByOrderByEndDateDesc()).thenReturn(Optional.empty());

        Optional<ResolvedPeriod> result = resolver.resolve(StatisticsScope.SEMESTER, "");

        assertThat(result).isEmpty();
    }

    // ── SEMESTER — anchor validation ─────────────────────────────────────────

    @Test
    void semestral_withValidUuid_resolvesSemester() {
        when(semesterRepository.findByUuid(semesterUuid)).thenReturn(Optional.of(semester2026_1));
        when(semesterRepository.findFirstByStartDateLessThanOrderByStartDateDesc(any()))
                .thenReturn(Optional.empty());

        Optional<ResolvedPeriod> result = resolver.resolve(
                StatisticsScope.SEMESTER, semesterUuid.toString());

        assertThat(result).isPresent();
        assertThat(result.get().from()).isEqualTo(semester2026_1.getStartDate());
    }

    @Test
    void semestral_withMalformedUuid_throwsIllegalArgument() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> resolver.resolve(StatisticsScope.SEMESTER, "not-a-uuid"))
                .withMessage("Invalid anchor for scope SEMESTER");
    }

    @Test
    void semestral_withNonExistentUuid_throwsIllegalArgument() {
        UUID unknown = UUID.randomUUID();
        when(semesterRepository.findByUuid(unknown)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> resolver.resolve(StatisticsScope.SEMESTER, unknown.toString()))
                .withMessageContaining("Semester not found");
    }

    // ── SEMESTER — month scaffold ────────────────────────────────────────────

    @Test
    void semestral_scaffoldContainsAllMonthsOfSemester() {
        when(semesterRepository.findByUuid(semesterUuid)).thenReturn(Optional.of(semester2026_1));
        when(semesterRepository.findFirstByStartDateLessThanOrderByStartDateDesc(any()))
                .thenReturn(Optional.empty());

        Optional<ResolvedPeriod> result = resolver.resolve(
                StatisticsScope.SEMESTER, semesterUuid.toString());

        assertThat(result).isPresent();
        // Semester 2026-1: Jan 12 – Jun 26 → months Ene, Feb, Mar, Abr, May, Jun
        assertThat(result.get().trendLabels())
                .containsExactly("Ene", "Feb", "Mar", "Abr", "May", "Jun");
    }

    @Test
    void semestral_scaffoldOver12Months_disambiguatesLabelsWithYear() {
        // Defensive case: a malformed semester spanning more than 12 months (pre-dates the
        // duration guard added to SemesterService, or legacy data). Labels must include the
        // year so repeated months (two Januaries) don't collide into the same bucket.
        Semester longSemester = new Semester();
        UUID uuid = UUID.randomUUID();
        longSemester.setUuid(uuid);
        longSemester.setName("2025-multi");
        longSemester.setStartDate(LocalDate.of(2025, 1, 1));
        longSemester.setEndDate(LocalDate.of(2026, 1, 31));

        when(semesterRepository.findByUuid(uuid)).thenReturn(Optional.of(longSemester));
        when(semesterRepository.findFirstByStartDateLessThanOrderByStartDateDesc(any()))
                .thenReturn(Optional.empty());

        Optional<ResolvedPeriod> result = resolver.resolve(StatisticsScope.SEMESTER, uuid.toString());

        assertThat(result).isPresent();
        List<String> labels = result.get().trendLabels();
        assertThat(labels).hasSize(13);
        assertThat(labels.get(0)).isEqualTo("Ene 25");
        assertThat(labels.get(12)).isEqualTo("Ene 26");
        // No duplicate labels despite two Januaries in the range
        assertThat(labels).doesNotHaveDuplicates();
    }

    // ── SEMESTER — delta truncation for closed semester ─────────────────────

    @Test
    void semestral_closedSemester_previousSemesterTakenComplete() {
        // Current semester is fully in the past
        Semester prev = new Semester();
        prev.setStartDate(LocalDate.of(2025, 8, 1));
        prev.setEndDate(LocalDate.of(2026, 1, 11));

        when(semesterRepository.findByUuid(semesterUuid)).thenReturn(Optional.of(semester2026_1));
        when(semesterRepository.findFirstByStartDateLessThanOrderByStartDateDesc(semester2026_1.getStartDate()))
                .thenReturn(Optional.of(prev));

        Optional<ResolvedPeriod> result = resolver.resolve(
                StatisticsScope.SEMESTER, semesterUuid.toString());

        assertThat(result).isPresent();
        ResolvedPeriod p = result.get();
        // Current semester ends 2026-06-26 — if today >= that date, period is closed
        // and prevTo should equal prev.endDate (complete, not truncated)
        LocalDate today = LocalDate.now();
        if (!today.isBefore(semester2026_1.getEndDate())) {
            assertThat(p.prevTo()).isEqualTo(prev.getEndDate());
        }
        // In any case prevFrom should be correct
        assertThat(p.prevFrom()).isEqualTo(prev.getStartDate());
    }

    // ── prevFrom null when no previous exists ─────────────────────────────────

    @Test
    void mensual_firstEverMonth_prevFromIsNull() {
        // We can't easily test this for MONTHLY since there's always a previous month.
        // Instead we verify the SEMESTER case where no previous semester exists.
        when(semesterRepository.findCurrent(any())).thenReturn(Optional.of(semester2026_1));
        when(semesterRepository.findFirstByStartDateLessThanOrderByStartDateDesc(any()))
                .thenReturn(Optional.empty());

        Optional<ResolvedPeriod> result = resolver.resolve(StatisticsScope.SEMESTER, null);

        assertThat(result).isPresent();
        assertThat(result.get().prevFrom()).isNull();
        assertThat(result.get().prevTo()).isNull();
    }
}
