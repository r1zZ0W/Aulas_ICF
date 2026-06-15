package mx.unam.icf.aulas.modules.reports.infrastructure;

import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.modules.reports.app.ReportPeriod;
import mx.unam.icf.aulas.modules.reports.app.ReservationReportService;
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

/**
 * REST controller for generating and downloading reservation PDF reports.
 *
 * <p>All endpoints are restricted to the {@code ADMIN} role and return
 * {@code application/pdf} with a {@code Content-Disposition: attachment} header.
 * Exposed under {@code /api/v1/reports}.</p>
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReservationReportService reportService;

    /**
     * Generates and downloads a PDF report of approved reservations for the requested period.
     * GET /api/v1/reports/reservations?period=MES_EN_CURSO|MES_ANTERIOR
     *
     * @param period report period; defaults to {@code MES_EN_CURSO} when omitted
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(value = "/reservations", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> reservationReport(
            @RequestParam(defaultValue = "MES_EN_CURSO") ReportPeriod period) {

        byte[] pdf = reportService.generatePdf(period);

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
}
