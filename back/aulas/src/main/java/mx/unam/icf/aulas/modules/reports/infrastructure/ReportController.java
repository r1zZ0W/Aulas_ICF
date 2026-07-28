package mx.unam.icf.aulas.modules.reports.infrastructure;

import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.infrastructure.web.controllers.ResponseHandler;
import mx.unam.icf.aulas.kernel.infrastructure.web.responses.ApiResponse;
import mx.unam.icf.aulas.modules.reports.app.ReservationStatisticsService;
import mx.unam.icf.aulas.modules.reports.app.dtos.ReservationStatisticsDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for reservation statistics.
 *
 * <p>All endpoints are restricted to the {@code ADMIN} role. Exposed under
 * {@code /api/v1/reports}.</p>
 *
 * <ul>
 *   <li>{@code GET /statistics} — returns aggregated JSON statistics for the dashboard.</li>
 *   <li>{@code GET /available-months} — returns the {@code yyyy-MM} months that have at least
 *       one active reservation, for the MONTHLY scope's period dropdown.</li>
 * </ul>
 *
 * <p>PDF export of the "Reportes y Estadísticas" dashboard is generated client-side from
 * this same statistics data (see the frontend's {@code exportStatisticsPdf.js}); there is
 * no server-rendered PDF endpoint.</p>
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController implements ResponseHandler {

    private final ReservationStatisticsService statisticsService;

    /**
     * Returns aggregated statistics for the "Reportes y Estadísticas" dashboard.
     *
     * <p>{@code GET /api/v1/reports/statistics?scope=MONTHLY|SEMESTER[&anchor=...]}</p>
     *
     * <p>Validation errors (unrecognised {@code scope}, malformed {@code anchor}, or a semester
     * UUID that does not exist) are thrown as {@link IllegalArgumentException} and mapped to
     * HTTP 400 by the global exception handler.</p>
     *
     * @param scope  period granularity: {@code "MONTHLY"} (default) or {@code "SEMESTER"};
     *               case-insensitive
     * @param anchor period anchor: a {@code yyyy-MM} string for monthly scope, or a semester UUID
     *               string for semester scope; {@code null}/blank triggers the period default
     *               (current month or current semester)
     * @return {@link ApiResponse} wrapping a {@link ReservationStatisticsDTO}
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<ReservationStatisticsDTO>> statistics(
            @RequestParam(defaultValue = "MONTHLY") String scope,
            @RequestParam(required = false) String anchor) {

       return ok(statisticsService.getStatistics(scope, anchor));
    }

    /**
     * Returns the {@code yyyy-MM} months that have at least one active reservation, newest first.
     *
     * <p>{@code GET /api/v1/reports/available-months}</p>
     *
     * <p>Used by the frontend to populate the MONTHLY scope's period dropdown with only the
     * months that actually have data, instead of a fixed rolling window of the last 12 months.</p>
     *
     * @return {@link ApiResponse} wrapping the list of {@code yyyy-MM} strings
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/available-months")
    public ResponseEntity<ApiResponse<List<String>>> availableMonths() {
        return ok(statisticsService.availableMonths());
    }
}
