package mx.unam.icf.aulas.modules.reports.infrastructure;

import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.infrastructure.web.controllers.ResponseHandler;
import mx.unam.icf.aulas.kernel.infrastructure.web.responses.ApiResponse;
import mx.unam.icf.aulas.modules.reports.app.ReportPeriod;
import mx.unam.icf.aulas.modules.reports.app.ReservationReportService;
import mx.unam.icf.aulas.modules.reports.app.ReservationStatisticsService;
import mx.unam.icf.aulas.modules.reports.app.dtos.ReservationStatisticsDTO;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * REST controller for reservation reports and statistics.
 *
 * <p>All endpoints are restricted to the {@code ADMIN} role. Exposed under
 * {@code /api/v1/reports}.</p>
 *
 * <ul>
 *   <li>{@code GET /reservations} — generates and streams a PDF report for a given month.</li>
 *   <li>{@code GET /statistics} — returns aggregated JSON statistics for the dashboard.</li>
 *   <li>{@code GET /available-months} — returns the {@code yyyy-MM} months that have at least
 *       one active reservation, for the MENSUAL scope's period dropdown.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController implements ResponseHandler {

    private final ReservationReportService     reportService;
    private final ReservationStatisticsService statisticsService;

    /**
     * Generates and downloads a PDF report of active reservations for the requested period.
     *
     * <p>{@code GET /api/v1/reports/reservations?period=MES_EN_CURSO|MES_ANTERIOR[&classroomUuid=...]}</p>
     *
     * @param period        report period; defaults to {@code MES_EN_CURSO} when omitted
     * @param classroomUuid optional UUID to filter by a specific classroom; omit for all classrooms
     * @return PDF bytes as {@code application/pdf} with a {@code Content-Disposition: attachment} header
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(value = "/reservations", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> reservationReport(
            @RequestParam(defaultValue = "MES_EN_CURSO") ReportPeriod period,
            @RequestParam(required = false) java.util.UUID classroomUuid) {

        byte[] pdf = reportService.generatePdf(period, classroomUuid);

        YearMonth month = period == ReportPeriod.MES_ANTERIOR
                ? YearMonth.now().minusMonths(1)
                : YearMonth.now();

        String filename = "reporte-reservas-" + month.format(DateTimeFormatter.ofPattern("yyyy-MM")) + ".pdf";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    /**
     * Returns aggregated statistics for the "Reportes y Estadísticas" dashboard.
     *
     * <p>{@code GET /api/v1/reports/statistics?scope=MENSUAL|SEMESTRAL[&anchor=...]}</p>
     *
     * <p>Validation errors (unrecognised {@code scope}, malformed {@code anchor}, or a semester
     * UUID that does not exist) are thrown as {@link IllegalArgumentException} and mapped to
     * HTTP 400 by the global exception handler.</p>
     *
     * @param scope  period granularity: {@code "MENSUAL"} (default) or {@code "SEMESTRAL"};
     *               case-insensitive
     * @param anchor period anchor: a {@code yyyy-MM} string for monthly scope, or a semester UUID
     *               string for semester scope; {@code null}/blank triggers the period default
     *               (current month or current semester)
     * @return {@link ApiResponse} wrapping a {@link ReservationStatisticsDTO}
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<ReservationStatisticsDTO>> statistics(
            @RequestParam(defaultValue = "MENSUAL") String scope,
            @RequestParam(required = false) String anchor) {

       return ok(statisticsService.getStatistics(scope, anchor));
    }

    /**
     * Returns the {@code yyyy-MM} months that have at least one active reservation, newest first.
     *
     * <p>{@code GET /api/v1/reports/available-months}</p>
     *
     * <p>Used by the frontend to populate the MENSUAL scope's period dropdown with only the
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
