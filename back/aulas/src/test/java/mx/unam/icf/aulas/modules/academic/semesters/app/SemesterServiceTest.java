package mx.unam.icf.aulas.modules.academic.semesters.app;

import mx.unam.icf.aulas.kernel.domain.exceptions.DomainException;
import mx.unam.icf.aulas.modules.academic.semesters.app.dtos.SemesterRequestDTO;
import mx.unam.icf.aulas.modules.academic.semesters.app.dtos.SemesterResponseDTO;
import mx.unam.icf.aulas.modules.academic.semesters.app.mappers.SemesterMapper;
import mx.unam.icf.aulas.modules.academic.semesters.domain.Semester;
import mx.unam.icf.aulas.modules.academic.semesters.infrastructure.SemesterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SemesterService}, focused on the semester-duration guard
 * ({@code MAX_SEMESTER_MONTHS = 12}).
 *
 * <p>This guard exists because {@link mx.unam.icf.aulas.modules.reports.app.StatisticsPeriodResolver}
 * derives the SEMESTRAL analysis range directly from {@code [startDate, endDate]} with no cap of
 * its own — an unbounded semester (e.g. a data-entry mistake spanning several years) would
 * silently distort the "Reportes y Estadísticas" trend chart. See
 * {@code StatisticsPeriodResolverTest#semestral_scaffoldOver12Months_disambiguatesLabelsWithYear}
 * for the defensive fix on the reporting side.</p>
 */
@ExtendWith(MockitoExtension.class)
class SemesterServiceTest {

    @Mock private SemesterRepository repository;
    @Mock private SemesterMapper     mapper;

    @InjectMocks
    private SemesterService service;

    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now();
        lenient().when(repository.findByName(any())).thenReturn(Optional.empty());
        lenient().when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(mapper.toEntity(any())).thenAnswer(inv -> {
            SemesterRequestDTO dto = inv.getArgument(0);
            Semester s = new Semester();
            s.setName(dto.name());
            s.setStartDate(dto.startDate());
            s.setEndDate(dto.endDate());
            return s;
        });
        lenient().when(mapper.toDto(any(Semester.class), any(LocalDate.class)))
                .thenReturn(new SemesterResponseDTO(
                        UUID.randomUUID(), "x", null, null, false, null, null));
    }

    // ── save (create) — duration guard ───────────────────────────────────────

    @Test
    void save_rejectsSemesterLongerThan12Months() {
        SemesterRequestDTO dto = new SemesterRequestDTO(
                "2025-multi",
                today.plusDays(1),
                today.plusDays(1).plusMonths(12).plusDays(1)); // 12 months + 1 day → exceeds cap

        assertThatExceptionOfType(DomainException.class)
                .isThrownBy(() -> service.save(dto))
                .withMessageContaining("12 months");
    }

    @Test
    void save_allowsTypicalSixMonthSemester() {
        SemesterRequestDTO dto = new SemesterRequestDTO(
                "2026-1", today.plusDays(1), today.plusDays(1).plusMonths(6));

        assertThatCode(() -> service.save(dto)).doesNotThrowAnyException();
    }

    @Test
    void save_allowsExactlyTwelveMonths() {
        LocalDate start = today.plusDays(1);
        SemesterRequestDTO dto = new SemesterRequestDTO(
                "2026-exact", start, start.plusMonths(12)); // exactly at the boundary → allowed

        assertThatCode(() -> service.save(dto)).doesNotThrowAnyException();
    }

    // ── update — duration guard ───────────────────────────────────────────────

    @Test
    void update_rejectsWideningToMoreThan12Months() {
        UUID uuid = UUID.randomUUID();
        Semester existing = new Semester();
        existing.setUuid(uuid);
        existing.setName("2026-1");
        existing.setStartDate(today.plusDays(1));
        existing.setEndDate(today.plusMonths(6));

        when(repository.findByUuid(uuid)).thenReturn(Optional.of(existing));

        SemesterRequestDTO dto = new SemesterRequestDTO(
                "2026-1",
                existing.getStartDate(),
                existing.getStartDate().plusMonths(12).plusDays(1)); // widened past the cap

        assertThatExceptionOfType(DomainException.class)
                .isThrownBy(() -> service.update(uuid, dto))
                .withMessageContaining("12 months");
    }
}
