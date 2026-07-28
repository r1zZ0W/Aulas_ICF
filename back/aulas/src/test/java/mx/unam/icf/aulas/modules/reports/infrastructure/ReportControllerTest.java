package mx.unam.icf.aulas.modules.reports.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.unam.icf.aulas.kernel.infrastructure.exceptions.GlobalExceptionHandler;
import mx.unam.icf.aulas.modules.reports.app.ReservationStatisticsService;
import mx.unam.icf.aulas.modules.reports.app.dtos.ReservationStatisticsDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice test for {@link ReportController} — exercises only the web layer with a
 * standalone {@link MockMvc} setup.
 *
 * <p>The {@link ReservationStatisticsService} is mocked so HTTP routing, parameter binding,
 * response shape, and error mapping are verified in isolation. {@code @PreAuthorize} annotations
 * are <em>not</em> tested here; security is covered by integration tests.</p>
 */
@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    @Mock private ReservationStatisticsService statisticsService;
    @Mock private Environment                  env;

    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ReportController controller = new ReportController(statisticsService);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(env))
                .build();
    }

    // ── GET /statistics — default params ─────────────────────────────────────

    @Test
    void statistics_defaultParams_returns200() throws Exception {
        when(statisticsService.getStatistics(eq("MONTHLY"), isNull()))
                .thenReturn(buildEmptyDto());

        mockMvc.perform(get("/api/v1/reports/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(false))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.totalReservations").value(0));
    }

    // ── GET /statistics — explicit scope and anchor ───────────────────────────

    @Test
    void statistics_explicitScopeAndAnchor_passedToService() throws Exception {
        when(statisticsService.getStatistics("SEMESTER", "some-uuid"))
                .thenReturn(buildEmptyDto());

        mockMvc.perform(get("/api/v1/reports/statistics")
                        .param("scope", "SEMESTER")
                        .param("anchor", "some-uuid"))
                .andExpect(status().isOk());

        verify(statisticsService).getStatistics("SEMESTER", "some-uuid");
    }

    // ── GET /statistics — ApiResponse envelope ────────────────────────────────

    @Test
    void statistics_responseEnvelope_hasCorrectStructure() throws Exception {
        ReservationStatisticsDTO dto = buildEmptyDto();
        when(statisticsService.getStatistics(any(), any())).thenReturn(dto);

        mockMvc.perform(get("/api/v1/reports/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(false))
                .andExpect(jsonPath("$.data.mostOccupiedClassrooms").isArray())
                .andExpect(jsonPath("$.data.topUsers").isArray())
                .andExpect(jsonPath("$.data.trend").isArray())
                .andExpect(jsonPath("$.data.recurrence").exists())
                .andExpect(jsonPath("$.data.recurrenceRatePct").value(0.0));
    }

    // ── GET /statistics — invalid scope → 400 ────────────────────────────────

    @Test
    void statistics_invalidScope_returns400() throws Exception {
        when(statisticsService.getStatistics(eq("WEEKLY"), any()))
                .thenThrow(new IllegalArgumentException("scope must be MONTHLY or SEMESTER"));
        // Simulate dev profile so the real message is forwarded
        when(env.acceptsProfiles(any(org.springframework.core.env.Profiles.class))).thenReturn(true);

        mockMvc.perform(get("/api/v1/reports/statistics").param("scope", "WEEKLY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(true));
    }

    @Test
    void statistics_invalidAnchor_returns400() throws Exception {
        when(statisticsService.getStatistics(any(), eq("bad-anchor")))
                .thenThrow(new IllegalArgumentException("Invalid anchor for scope MONTHLY"));
        when(env.acceptsProfiles(any(org.springframework.core.env.Profiles.class))).thenReturn(true);

        mockMvc.perform(get("/api/v1/reports/statistics")
                        .param("scope", "MONTHLY")
                        .param("anchor", "bad-anchor"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(true));
    }

    @Test
    void statistics_nonExistentSemesterUuid_returns400() throws Exception {
        String fakeUuid = "00000000-0000-0000-0000-000000000000";
        when(statisticsService.getStatistics(eq("SEMESTER"), eq(fakeUuid)))
                .thenThrow(new IllegalArgumentException("Semester not found: " + fakeUuid));
        when(env.acceptsProfiles(any(org.springframework.core.env.Profiles.class))).thenReturn(true);

        mockMvc.perform(get("/api/v1/reports/statistics")
                        .param("scope", "SEMESTER")
                        .param("anchor", fakeUuid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(true));
    }

    // ── GET /available-months ─────────────────────────────────────────────────

    @Test
    void availableMonths_returns200_withMonthsFromService() throws Exception {
        when(statisticsService.availableMonths())
                .thenReturn(List.of("2026-06", "2026-05", "2025-11"));

        mockMvc.perform(get("/api/v1/reports/available-months"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(false))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0]").value("2026-06"))
                .andExpect(jsonPath("$.data[1]").value("2026-05"))
                .andExpect(jsonPath("$.data[2]").value("2025-11"));
    }

    @Test
    void availableMonths_emptyWhenNoReservations() throws Exception {
        when(statisticsService.availableMonths()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/reports/available-months"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns a fully-initialised DTO with zeroed-out values for use in response-shape tests. */
    private ReservationStatisticsDTO buildEmptyDto() {
        return new ReservationStatisticsDTO(
                0L, null, null, null, 0.0,
                List.of(), List.of(),
                new ReservationStatisticsDTO.Recurrence(0L, 0L),
                List.of(new ReservationStatisticsDTO.TrendPoint("01", 0L))
        );
    }
}
